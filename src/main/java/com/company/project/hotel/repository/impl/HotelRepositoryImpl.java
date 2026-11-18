package com.company.project.hotel.repository.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.company.project.hotel.model.AmenityScope;
import com.company.project.hotel.repository.FilterCountsView;
import com.company.project.hotel.repository.FilterGroup;
import com.company.project.hotel.repository.FilterOptionView;
import com.company.project.hotel.repository.GalleryPhotoView;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelContactView;
import com.company.project.hotel.repository.HotelDetailView;
import com.company.project.hotel.repository.HotelFacilityView;
import com.company.project.hotel.repository.HotelFilter;
import com.company.project.hotel.repository.HotelJpql;
import com.company.project.hotel.repository.HotelOfferView;
import com.company.project.hotel.repository.HotelOrder;
import com.company.project.hotel.repository.HotelPhotoView;
import com.company.project.hotel.repository.HotelRepositoryCustom;
import com.company.project.hotel.repository.HotelSql;
import com.company.project.hotel.repository.HotelSuggestionView;
import com.company.project.hotel.repository.OfferLineView;
import com.company.project.hotel.repository.RoomRateView;
import com.company.project.hotel.repository.StayRequirement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

/**
 * Hand-written hotel queries. Spring Data finds this class by its name ({@code HotelRepository} + "Impl") and
 * merges it into the {@code HotelRepository} bean, so callers only ever inject that.
 * <p>
 * JPQL everywhere except the priced availability check. That one is native SQL because it needs one row per night
 * of the stay ({@code generate_series}), where a night without an inventory row counts as the room type's default
 * capacity; the SQL function {@code night_price()} per rate plan and night; one cheapest rate plan per room type
 * ({@code distinct on}); a running sum of free rooms to fill the requested rooms from the cheapest room type up; and
 * array columns of facilities, beds and room features for the filters. It returns ids, quantities and prices only;
 * JPQL loads the cards and the names. The hotel page's room list is native for the same reason and reuses the chain's
 * first steps ({@code nights}, {@code free_rooms}, {@code rate_offers}), so both agree on what can be sold. The
 * commission rate is native too, because partners have no entity yet.
 * <p>
 * Filters ({@link HotelFilterSql}) are predicates over the rows of that query, with fixed aliases: {@code c} = a
 * {@code hotel_rows} row, {@code o} = an {@code offers} row, {@code v} = an {@code available} or {@code results} row,
 * {@code h} = hotels. Hotel filters narrow {@code candidate_hotels} before anything is priced; offer filters narrow
 * {@code scoped_offers}, so the offer is the cheapest mix of the rate plans left.
 * <p>
 * LIKE on typed text says {@code escape '\'}: Hibernate otherwise renders {@code escape ''}, which would turn
 * the caller's backslash escapes into literal characters.
 */
@RequiredArgsConstructor
public class HotelRepositoryImpl implements HotelRepositoryCustom {

	private static final String SUGGEST = """
			select new com.company.project.hotel.repository.HotelSuggestionView(h.slug, h.name, a.district, a.city)
			from Hotel h
			join h.address a
			where\s""" + HotelJpql.HOTEL_LISTED + """

			  and (norm(h.name) like concat(norm(:query), '%') escape '\\'
			       or norm(h.name) like concat('% ', norm(:query), '%') escape '\\')
			order by case when norm(h.name) like concat(norm(:query), '%') escape '\\' then 0 else 1 end,
			         h.userRatingCount desc, h.name""";

	private static final String CARDS_BY_ID = HotelJpql.CARD_SELECT + "where h.id in :ids";

	private static final String LISTED_IDS = "select h.id from Hotel h where h.id in :ids and " + HotelJpql.HOTEL_LISTED;

	private static final String TOP_IN_DESTINATION = HotelJpql.CARD_SELECT
			+ "join Destination d on d.id = :destinationId\nwhere " + HotelJpql.HOTEL_LISTED
			+ "\n  and " + HotelJpql.ADDRESS_IN_DESTINATION
			+ "\norder by h.userRatingAvg desc nulls last, h.googleRating desc nulls last, h.id";

	// The COUNTRY destination row is joined only for its current name, like the place suggestions' labels.
	private static final String DETAIL = """
			select new com.company.project.hotel.repository.HotelDetailView(
			       h.id, h.slug, h.name, t.code, t.name, h.starRating, h.description,
			       a.line1, a.ward, a.district, a.city, a.countryCode, c.name, a.latitude, a.longitude,
			       h.distanceToCenterM, h.googleRating, h.googleRatingCount, h.userRatingAvg, h.userRatingCount,
			       h.checkInFrom, h.checkInUntil, h.checkOutUntil, h.minCheckInAge)
			from Hotel h
			join h.address a
			join h.accommodationType t
			left join Destination c on c.countryCode = a.countryCode and c.deletedAt is null
			     and c.type = com.company.project.destination.model.DestinationType.COUNTRY
			where h.slug = :slug and\s""" + HotelJpql.HOTEL_LISTED;

	// Room photos only of room types that are on sale; one stored without a category counts as a room photo.
	private static final String GALLERY = """
			select new com.company.project.hotel.repository.GalleryPhotoView(
			       p.url, coalesce(p.category, case when p.roomTypeId is not null then 'room' end), p.caption,
			       p.roomTypeId)
			from HotelPhoto p
			left join RoomType rt on rt.id = p.roomTypeId
			where p.hotel.id = :hotelId
			  and (p.roomTypeId is null
			       or (rt.status = com.company.project.hotel.model.ListingStatus.ACTIVE and rt.deletedAt is null))
			order by case when p.roomTypeId is null then 0 else 1 end, p.cover desc, p.sortOrder, p.id""";

	// Groups and names in the same order as the facility filter options.
	private static final String FACILITIES = """
			select new com.company.project.hotel.repository.HotelFacilityView(am.groupName, am.code, am.name, am.icon,
			       f.free)
			from Hotel h
			join h.facilities f
			join f.amenity am
			where h.id = :hotelId and am.scope = com.company.project.hotel.model.AmenityScope.HOTEL
			order by am.groupName, am.name""";

	private static final String MAIN_CURRENCY_IN_DESTINATION = """
			select h.currency
			from Hotel h
			join h.address a
			join Destination d on d.id = :destinationId
			where\s""" + HotelJpql.HOTEL_LISTED + "\n  and " + HotelJpql.ADDRESS_IN_DESTINATION + """

			group by h.currency
			order by count(h) desc, h.currency""";

	/**
	 * {@code hotel_rows}: the listed hotels of the location with what the hotel filters read, facilities as an array
	 * of codes. Append the location condition, the name condition and {@link #HOTEL_ROWS_END}.
	 */
	private static final String HOTEL_ROWS = """
			with hotel_rows as (
			    select h.id, h.star_rating, h.google_rating, h.user_rating_avg, h.distance_to_center_m,
			           t.code as accommodation_type,
			           array(select cast(am.code as text)
			                 from hotel_amenities ha
			                 join amenities am on am.id = ha.amenity_id
			                 where ha.hotel_id = h.id and am.scope = 'HOTEL') as facilities
			    from hotels h
			    join addresses a on a.id = h.address_id
			    join accommodation_types t on t.id = h.accommodation_type_id
			""";

	private static final String IN_DESTINATION = "    join destinations d on d.id = :locationId\n    where "
			+ HotelSql.HOTEL_LISTED + " and " + HotelSql.ADDRESS_IN_DESTINATION;

	private static final String IS_HOTEL = "    where h.id = :locationId and " + HotelSql.HOTEL_LISTED;

	private static final String HOTEL_ROWS_END = "\n),\n";

	/**
	 * Room type {@code rt} holds the guests of one room, {@code :adultsPerRoom} adults and {@code :childrenPerRoom}
	 * children; children may also use places adults leave free. The only definition of "fits": the search keeps only
	 * fitting room types, the hotel page's room list reports it per room type.
	 */
	private static final String ROOM_TYPE_FITS = """
			rt.max_adults >= :adultsPerRoom
			      and rt.max_occupancy >= :adultsPerRoom + :childrenPerRoom
			      and :childrenPerRoom <= rt.max_children + (rt.max_adults - :adultsPerRoom)""";

	/** {@code nights}: one row per night of {@code [:checkIn, :checkOut)}. */
	private static final String NIGHTS = """
			nights as (
			    select cast(generate_series(cast(:checkIn as date), cast(:checkOut as date) - 1, interval '1 day') as date)
			           as stay_date
			)""";

	/**
	 * {@code free_rooms}: the active room types of {@code candidate_hotels} and their fewest free rooms on any night.
	 * Append any extra room type condition (starting with {@code "\n      and "}) and {@link #FREE_ROOMS_END}.
	 */
	private static final String FREE_ROOMS = """
			free_rooms as (
			    select rt.hotel_id, rt.id as room_type_id,
			           min(case when coalesce(i.is_closed, false) then 0
			                    else coalesce(i.total_rooms, rt.total_rooms) - coalesce(i.booked_rooms, 0) end) as free
			    from room_types rt
			    join candidate_hotels c on c.id = rt.hotel_id
			    cross join nights n
			    left join room_inventory i on i.room_type_id = rt.id and i.stay_date = n.stay_date
			    where rt.status = 'ACTIVE' and rt.deleted_at is null""";

	private static final String FREE_ROOMS_END = """

			    group by rt.hotel_id, rt.id
			)""";

	/**
	 * {@code rate_offers}: the active rate plans of the {@code free_rooms} with a room free, in the hotel's currency,
	 * priced for the stay, without a closed night and with the stay at least as long as the longest minimum stay of
	 * its nights. The only definition of "can sell the stay", shared by the search and the room list.
	 */
	private static final String RATE_OFFERS = """
			rate_offers as (
			    select f.hotel_id, f.room_type_id, f.free, rp.id as rate_plan_id, rp.currency,
			           cast(sum(night_price(rp.id, n.stay_date)) as bigint) as stay_price
			    from free_rooms f
			    join hotels h on h.id = f.hotel_id
			    join rate_plans rp on rp.room_type_id = f.room_type_id and rp.is_active and rp.deleted_at is null
			                      and rp.currency = h.currency
			    cross join nights n
			    left join rate_plan_prices pp on pp.rate_plan_id = rp.id and pp.stay_date = n.stay_date
			    where f.free > 0
			    group by f.hotel_id, f.room_type_id, f.free, rp.id, rp.currency
			    having not bool_or(coalesce(pp.is_closed, false))
			       and max(coalesce(pp.min_nights, rp.min_nights)) <= :nights
			)""";

	/**
	 * Rooms and rate plans of {@code candidate_hotels} that can sell {@code :rooms} rooms for every night of
	 * {@code [:checkIn, :checkOut)}, and what the offer filters read: {@code nights}, {@code free_rooms} of the fitting
	 * room types, {@code rate_offers}, and {@code offers}, which adds the board code, cancellation ({@code FREE} while
	 * free cancellation is still possible at {@code :now}, {@code NON_REFUNDABLE}, else null) and the room type's bed
	 * and feature codes.
	 */
	private static final String PRICED_OFFERS = NIGHTS + ",\n" + FREE_ROOMS + "\n      and " + ROOM_TYPE_FITS
			+ FREE_ROOMS_END + ",\n" + RATE_OFFERS + ",\n" + """
			offers as (
			    select r.*, b.code as board_type,
			           case when free_cancellation_until(cp.id, cast(:checkIn as date), h.check_in_from, h.timezone) > :now
			                then 'FREE'
			                when not cp.is_refundable then 'NON_REFUNDABLE' end as cancellation,
			           array(select cast(bt.code as text)
			                 from room_type_beds rb
			                 join bed_types bt on bt.id = rb.bed_type_id
			                 where rb.room_type_id = r.room_type_id) as bed_types,
			           array(select cast(am.code as text)
			                 from room_type_amenities ra
			                 join amenities am on am.id = ra.amenity_id
			                 where ra.room_type_id = r.room_type_id and am.scope = 'ROOM') as room_features
			    from rate_offers r
			    join rate_plans rp on rp.id = r.rate_plan_id
			    join board_types b on b.id = rp.board_type_id
			    join cancellation_policies cp on cp.id = rp.cancellation_policy_id
			    join hotels h on h.id = r.hotel_id
			),
			""";

	/**
	 * The room list of one hotel: every active room type is a candidate, fitting or not, and each rate plan that can
	 * sell the stay is one row, fitting room types first, then cheapest first. {@code night_prices} calls
	 * {@code night_price()} again for this hotel's plans only, for penalties counted in nights.
	 */
	private static final String ROOM_RATES = "with candidate_hotels as (select h.id from hotels h where h.id = :hotelId and "
			+ HotelSql.HOTEL_LISTED + "),\n" + NIGHTS + ",\n" + FREE_ROOMS + FREE_ROOMS_END + ",\n" + RATE_OFFERS + """

			select r.room_type_id, r.rate_plan_id, r.free, (%s) as fits, r.stay_price,
			       array(select night_price(r.rate_plan_id, n.stay_date) from nights n order by n.stay_date) as night_prices
			from rate_offers r
			join room_types rt on rt.id = r.room_type_id
			order by fits desc, r.stay_price, rt.sort_order, rt.id, r.rate_plan_id""".formatted(ROOM_TYPE_FITS);

	/** Opens {@code scoped_offers} with the offers the results use; append the offer predicates and {@code ")"}. */
	private static final String ALL_SCOPE = """
			scoped_offers as (
			    select 'ALL' as facet, '' as facet_value, o.hotel_id, o.room_type_id, o.free, o.rate_plan_id, o.currency,
			           o.stay_price
			    from offers o
			    where\s""";

	/**
	 * The cheapest offer of each hotel within each scope of {@code scoped_offers} ({@code facet}, {@code facet_value}):
	 * the results use the single scope {@code 'ALL'}.
	 * <ul>
	 * <li>{@code cheapest_rates}: the cheapest rate plan per room type (ties: lowest rate plan id).</li>
	 * <li>{@code picked}: rooms taken from the cheapest room type up (ties: lowest room type id) until {@code :rooms}
	 * are covered.</li>
	 * <li>{@code available}: the hotels where they are, with the offer's total for the stay.</li>
	 * </ul>
	 */
	private static final String SCOPED_FILL = """
			,
			cheapest_rates as (
			    select distinct on (facet, facet_value, room_type_id)
			           facet, facet_value, hotel_id, room_type_id, free, rate_plan_id, currency, stay_price
			    from scoped_offers
			    order by facet, facet_value, room_type_id, stay_price, rate_plan_id
			),
			filled as (
			    select c.*, sum(free) over (partition by facet, facet_value, hotel_id order by stay_price, room_type_id
			                                rows unbounded preceding) - free as rooms_before
			    from cheapest_rates c
			),
			picked as (
			    select facet, facet_value, hotel_id, room_type_id, rate_plan_id, currency, stay_price,
			           least(free, :rooms - rooms_before) as quantity
			    from filled
			    where rooms_before < :rooms
			),
			available as (
			    select facet, facet_value, hotel_id, min(currency) as currency,
			           cast(sum(quantity * stay_price) as bigint) as total
			    from picked
			    group by facet, facet_value, hotel_id
			    having sum(quantity) >= :rooms
			)
			""";

	/**
	 * {@code results}: the hotels the list shows, and whether each sells in the price currency (the price sort lists
	 * those first). Append the price predicate and {@code ")"}.
	 */
	private static final String RESULTS = """
			, results as (
			    select v.hotel_id, v.currency, v.total, v.currency = :priceCurrency as in_price_currency
			    from available v
			    where v.facet = 'ALL' and\s""";

	/**
	 * The offer lines of one page of results in the requested order (the hotel id keeps it stable across pages);
	 * within a hotel, cheapest line first. Append the order by items and {@link #PAGE_LINES_END}.
	 */
	private static final String PAGE_LINES = """
			),
			page as (
			    select h.id, row_number() over (order by\s""";

	private static final String PAGE_LINES_END = """
			) as position
			    from results v
			    join hotels h on h.id = v.hotel_id
			    order by position
			    limit :limit offset :offset
			)
			select p.hotel_id, p.room_type_id, p.rate_plan_id, p.quantity, p.stay_price, p.currency
			from picked p
			join page on page.id = p.hotel_id
			where p.facet = 'ALL'
			order by page.position, p.stay_price, p.room_type_id""";

	private static final String COUNT_RESULTS = ")\nselect count(*) from results";

	/**
	 * One more scope of {@code scoped_offers}: the offers an option of an offer filter allows, with the other offer
	 * filters. Arguments: group name, option value expression, extra join, predicates.
	 */
	private static final String OPTION_SCOPE = """

			    union all
			    select '%1$s', %2$s, o.hotel_id, o.room_type_id, o.free, o.rate_plan_id, o.currency, o.stay_price
			    from offers o%3$s
			    where %4$s""";

	/**
	 * The final selects of the filter counts, each a {@code union all} part giving {@code (facet, facet_value,
	 * hotels, min_total, max_total)} over the available offers {@code v} of the hotels {@code c}. Every part applies
	 * the price predicate and the hotel predicates its group is counted against (arguments in that order).
	 */
	private static final String COUNT_BY_COLUMN = """
			select '%1$s', cast(%2$s as text), count(*), cast(null as bigint), cast(null as bigint)
			from available v
			join hotel_rows c on c.id = v.hotel_id
			where v.facet = 'ALL' and %2$s is not null and %3$s and %4$s
			group by %2$s""";

	/** Hotels at or within each fixed value ({@code t.threshold}) of a numeric filter. */
	private static final String COUNT_BY_THRESHOLD = """
			select '%1$s', cast(t.threshold as text), count(*), cast(null as bigint), cast(null as bigint)
			from available v
			join hotel_rows c on c.id = v.hotel_id
			cross join unnest(array[%2$s]) as t(threshold)
			where v.facet = 'ALL' and %3$s and %4$s and %5$s
			group by t.threshold""";

	private static final String COUNT_FACILITIES = """
			select 'FACILITIES', opt.code, count(*), cast(null as bigint), cast(null as bigint)
			from available v
			join hotel_rows c on c.id = v.hotel_id
			cross join unnest(c.facilities) as opt(code)
			where v.facet = 'ALL' and %1$s and %2$s
			group by opt.code""";

	private static final String COUNT_OFFER_OPTIONS = """
			select v.facet, v.facet_value, count(*), cast(null as bigint), cast(null as bigint)
			from available v
			join hotel_rows c on c.id = v.hotel_id
			where v.facet <> 'ALL' and %1$s and %2$s
			group by v.facet, v.facet_value""";

	/** The offer totals of the results without the price filter, in the price currency. */
	private static final String PRICE_RANGE = """
			select 'PRICE', '', count(*), min(v.total), max(v.total)
			from available v
			join hotel_rows c on c.id = v.hotel_id
			where v.facet = 'ALL' and v.currency = :priceCurrency and %1$s""";

	private static final String FACILITY_OPTIONS = """
			select new com.company.project.hotel.repository.FilterOptionView(a.code, a.name, a.groupName)
			from Amenity a
			where a.scope = :scope
			order by a.groupName, a.name""";

	// Native: partners has no entity until the partner stories (O-ACC-01), so JPQL can't reach it.
	private static final String COMMISSION_RATE = """
			select p.commission_rate from hotels h join partners p on p.id = h.partner_id where h.id = :hotelId""";

	// Native for the same reason as COMMISSION_RATE.
	private static final String PARTNER_CONTACT_EMAIL = """
			select p.contact_email from hotels h join partners p on p.id = h.partner_id where h.id = :hotelId""";

	// Not filtered by HOTEL_LISTED: a booking keeps showing its hotel after the listing ends. The COUNTRY destination
	// row only gives the country's current name, as on the hotel page.
	private static final String CONTACT = """
			select new com.company.project.hotel.repository.HotelContactView(
			       h.id, h.slug, h.name, a.line1, a.ward, a.district, a.city, a.countryCode, c.name, a.latitude,
			       a.longitude, h.phone, h.email, h.timezone, h.checkInFrom, h.checkOutUntil)
			from Hotel h
			join h.address a
			left join Destination c on c.countryCode = a.countryCode and c.deletedAt is null
			     and c.type = com.company.project.destination.model.DestinationType.COUNTRY
			where h.id = :hotelId""";

	private static final String HOTEL_PHOTOS = """
			select new com.company.project.hotel.repository.HotelPhotoView(p.hotel.id, p.url)
			from HotelPhoto p
			where p.hotel.id in :ids and p.roomTypeId is null
			order by p.hotel.id, p.cover desc, p.sortOrder, p.id""";

	private final EntityManager entityManager;

	@Override
	public List<HotelSuggestionView> suggest(String query, int limit) {
		return entityManager.createQuery(SUGGEST, HotelSuggestionView.class)
			.setParameter("query", query)
			.setMaxResults(limit)
			.getResultList();
	}

	@Override
	public List<HotelCardView> findCardsByIdIn(Collection<Long> ids) {
		return entityManager.createQuery(CARDS_BY_ID, HotelCardView.class).setParameter("ids", ids).getResultList();
	}

	@Override
	public List<Long> findListedIds(Collection<Long> ids) {
		return entityManager.createQuery(LISTED_IDS, Long.class).setParameter("ids", ids).getResultList();
	}

	@Override
	public List<HotelCardView> findTopInDestination(long destinationId, int limit) {
		return entityManager.createQuery(TOP_IN_DESTINATION, HotelCardView.class)
			.setParameter("destinationId", destinationId)
			.setMaxResults(limit)
			.getResultList();
	}

	@Override
	public List<HotelPhotoView> findHotelPhotos(Collection<Long> hotelIds) {
		return entityManager.createQuery(HOTEL_PHOTOS, HotelPhotoView.class).setParameter("ids", hotelIds).getResultList();
	}

	@Override
	public Optional<HotelDetailView> findDetail(String slug) {
		return entityManager.createQuery(DETAIL, HotelDetailView.class)
			.setParameter("slug", slug)
			.getResultStream()
			.findFirst();
	}

	@Override
	public List<GalleryPhotoView> findGallery(long hotelId) {
		return entityManager.createQuery(GALLERY, GalleryPhotoView.class).setParameter("hotelId", hotelId).getResultList();
	}

	@Override
	public List<HotelFacilityView> findFacilities(long hotelId) {
		return entityManager.createQuery(FACILITIES, HotelFacilityView.class)
			.setParameter("hotelId", hotelId)
			.getResultList();
	}

	@Override
	public Optional<String> findMainCurrencyInDestination(long destinationId) {
		return entityManager.createQuery(MAIN_CURRENCY_IN_DESTINATION, String.class)
			.setParameter("destinationId", destinationId)
			.setMaxResults(1)
			.getResultStream()
			.findFirst();
	}

	@Override
	public Page<HotelOfferView> findAvailableInDestination(long destinationId, StayRequirement stay,
			HotelFilter filter, HotelOrder order, Pageable pageable) {
		return availableOffers(IN_DESTINATION, destinationId, stay, filter, order, pageable);
	}

	@Override
	public Page<HotelOfferView> findAvailableHotel(long hotelId, StayRequirement stay, HotelFilter filter,
			HotelOrder order, Pageable pageable) {
		return availableOffers(IS_HOTEL, hotelId, stay, filter, order, pageable);
	}

	@Override
	public BigDecimal findCommissionRate(long hotelId) {
		return (BigDecimal) entityManager.createNativeQuery(COMMISSION_RATE)
			.setParameter("hotelId", hotelId)
			.getSingleResult();
	}

	@Override
	public Optional<HotelContactView> findContact(long hotelId) {
		return entityManager.createQuery(CONTACT, HotelContactView.class)
			.setParameter("hotelId", hotelId)
			.getResultStream()
			.findFirst();
	}

	@Override
	public Optional<String> findPartnerContactEmail(long hotelId) {
		return entityManager.createNativeQuery(PARTNER_CONTACT_EMAIL, String.class)
			.setParameter("hotelId", hotelId)
			.getResultStream()
			.findFirst();
	}

	@Override
	@SuppressWarnings("unchecked") // a native query without a result class returns Object[] rows
	public List<RoomRateView> findRoomRates(long hotelId, StayRequirement stay) {
		// Only the parameters the text uses: Hibernate rejects binding any other.
		List<Object[]> rows = entityManager.createNativeQuery(ROOM_RATES)
			.setParameter("hotelId", hotelId)
			.setParameter("checkIn", stay.checkIn())
			.setParameter("checkOut", stay.checkOut())
			.setParameter("nights", stay.nights())
			.setParameter("adultsPerRoom", stay.adultsPerRoom())
			.setParameter("childrenPerRoom", stay.childrenPerRoom())
			.getResultList();
		return rows.stream()
			.map(row -> new RoomRateView(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
					((Number) row[2]).intValue(), (Boolean) row[3], ((Number) row[4]).longValue(), nightPrices(row[5])))
			.toList();
	}

	@Override
	public FilterCountsView findFilterCountsInDestination(long destinationId, StayRequirement stay, HotelFilter filter) {
		return filterCounts(IN_DESTINATION, destinationId, stay, filter);
	}

	@Override
	public FilterCountsView findFilterCountsForHotel(long hotelId, StayRequirement stay, HotelFilter filter) {
		return filterCounts(IS_HOTEL, hotelId, stay, filter);
	}

	@Override
	public Map<FilterGroup, List<FilterOptionView>> findFilterOptions() {
		Map<FilterGroup, List<FilterOptionView>> options = new EnumMap<>(FilterGroup.class);
		options.put(FilterGroup.ACCOMMODATION_TYPE, entityManager.createQuery("""
				select new com.company.project.hotel.repository.FilterOptionView(t.code, t.name)
				from AccommodationType t order by t.id""", FilterOptionView.class).getResultList());
		options.put(FilterGroup.FACILITIES, entityManager.createQuery(FACILITY_OPTIONS, FilterOptionView.class)
			.setParameter("scope", AmenityScope.HOTEL)
			.getResultList());
		options.put(FilterGroup.BOARD_TYPE, entityManager.createQuery("""
				select new com.company.project.hotel.repository.FilterOptionView(b.code, b.name)
				from BoardType b order by b.id""", FilterOptionView.class).getResultList());
		options.put(FilterGroup.BED_TYPE, entityManager.createQuery("""
				select new com.company.project.hotel.repository.FilterOptionView(b.code, b.name)
				from BedType b order by b.id""", FilterOptionView.class).getResultList());
		options.put(FilterGroup.ROOM_FEATURES, entityManager.createQuery(FACILITY_OPTIONS, FilterOptionView.class)
			.setParameter("scope", AmenityScope.ROOM)
			.getResultList());
		return options;
	}

	/**
	 * {@code hotel_rows} through {@code available}: the candidates are the hotel rows passing
	 * {@code candidatePredicates}, and {@code scoped_offers} holds the results' scope plus {@code optionScopes}.
	 */
	private static String scopedAvailability(String location, HotelFilterSql filters, String candidatePredicates,
			String optionScopes) {
		return HOTEL_ROWS + location + filters.nameCondition() + HOTEL_ROWS_END
				+ "candidate_hotels as (select c.id from hotel_rows c where " + candidatePredicates + "),\n"
				+ PRICED_OFFERS + ALL_SCOPE + filters.offerPredicates() + optionScopes + ")" + SCOPED_FILL;
	}

	@SuppressWarnings("unchecked") // a native query without a result class returns Object[] rows
	private Page<HotelOfferView> availableOffers(String location, long locationId, StayRequirement stay,
			HotelFilter filter, HotelOrder order, Pageable pageable) {
		HotelFilterSql filters = new HotelFilterSql(filter);
		String results = scopedAvailability(location, filters, filters.hotelPredicates(), "") + RESULTS
				+ filters.price(stay.nights());
		Query pageQuery = entityManager.createNativeQuery(
				results + PAGE_LINES + HotelFilterSql.order(order) + PAGE_LINES_END);
		List<Object[]> rows = filters.bind(bind(pageQuery, locationId, stay, filter))
			.setParameter("limit", pageable.getPageSize())
			.setParameter("offset", pageable.getOffset())
			.getResultList();
		// Rows come grouped by hotel in page order; keep that order.
		Map<Long, List<OfferLineView>> linesByHotel = new LinkedHashMap<>();
		for (Object[] row : rows) {
			OfferLineView line = new OfferLineView(((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
					((Number) row[2]).longValue(), ((Number) row[3]).intValue(), ((Number) row[4]).longValue(),
					(String) row[5]);
			linesByHotel.computeIfAbsent(line.hotelId(), id -> new ArrayList<>()).add(line);
		}
		List<HotelOfferView> offers = linesByHotel.entrySet()
			.stream()
			.map(entry -> new HotelOfferView(entry.getKey(), List.copyOf(entry.getValue())))
			.toList();
		// Counts only when the page alone cannot tell the total.
		return PageableExecutionUtils.getPage(offers, pageable,
				() -> ((Number) filters.bind(bind(entityManager.createNativeQuery(results + COUNT_RESULTS), locationId,
						stay, filter)).getSingleResult()).longValue());
	}

	/**
	 * Every hotel of the location is a candidate, since a hotel failing one filter still counts for that filter's
	 * options; each option of an offer filter gets its own scope, so its count prices offers the way the list would.
	 */
	@SuppressWarnings("unchecked") // a native query without a result class returns Object[] rows
	private FilterCountsView filterCounts(String location, long locationId, StayRequirement stay, HotelFilter filter) {
		HotelFilterSql filters = new HotelFilterSql(filter);
		String optionScopes = OPTION_SCOPE.formatted("BOARD_TYPE", "o.board_type", "",
				filters.offerPredicates(FilterGroup.BOARD_TYPE))
				+ OPTION_SCOPE.formatted("CANCELLATION", "o.cancellation", "",
						"o.cancellation is not null and " + filters.offerPredicates(FilterGroup.CANCELLATION))
				+ OPTION_SCOPE.formatted("BED_TYPE", "opt.code", "\n    cross join unnest(o.bed_types) as opt(code)",
						filters.offerPredicates(FilterGroup.BED_TYPE))
				// An AND filter: each feature narrows the current selection, so it keeps every offer predicate.
				+ OPTION_SCOPE.formatted("ROOM_FEATURES", "opt.code",
						"\n    cross join unnest(o.room_features) as opt(code)", filters.offerPredicates());
		String price = filters.price(stay.nights());
		String counts = String.join("\nunion all\n",
				COUNT_BY_COLUMN.formatted("STARS", "c.star_rating", price,
						filters.hotelPredicates(FilterGroup.STARS)),
				threshold(FilterGroup.GOOGLE_RATING, "c.google_rating >= t.threshold", price, filters),
				threshold(FilterGroup.USER_RATING, "c.user_rating_avg >= t.threshold", price, filters),
				COUNT_BY_COLUMN.formatted("ACCOMMODATION_TYPE", "c.accommodation_type", price,
						filters.hotelPredicates(FilterGroup.ACCOMMODATION_TYPE)),
				// An AND filter, like room features.
				COUNT_FACILITIES.formatted(price, filters.hotelPredicates()),
				threshold(FilterGroup.DISTANCE, "c.distance_to_center_m <= t.threshold", price, filters),
				COUNT_OFFER_OPTIONS.formatted(price, filters.hotelPredicates()),
				PRICE_RANGE.formatted(filters.hotelPredicates()));
		String sql = scopedAvailability(location, filters, "true", optionScopes) + counts;
		List<Object[]> rows = filters.bind(bind(entityManager.createNativeQuery(sql), locationId, stay, filter))
			.getResultList();
		Map<FilterGroup, Map<String, Long>> hotels = new EnumMap<>(FilterGroup.class);
		Long minTotal = null;
		Long maxTotal = null;
		for (Object[] row : rows) {
			if ("PRICE".equals(row[0])) {
				minTotal = row[3] == null ? null : ((Number) row[3]).longValue();
				maxTotal = row[4] == null ? null : ((Number) row[4]).longValue();
			}
			else {
				hotels.computeIfAbsent(FilterGroup.valueOf((String) row[0]), group -> new HashMap<>())
					.put((String) row[1], ((Number) row[2]).longValue());
			}
		}
		return new FilterCountsView(hotels, minTotal, maxTotal);
	}

	// The fixed values are constants of FilterGroup, never request data.
	private static String threshold(FilterGroup group, String condition, String price, HotelFilterSql filters) {
		return COUNT_BY_THRESHOLD.formatted(group.name(), String.join(", ", group.optionValues()), condition, price,
				filters.hotelPredicates(group));
	}

	// Hibernate hands a SQL bigint[] column of a native query back as a Long[].
	private static List<Long> nightPrices(Object column) {
		return List.of((Long[]) column);
	}

	private static Query bind(Query query, long locationId, StayRequirement stay, HotelFilter filter) {
		return query.setParameter("locationId", locationId)
			.setParameter("checkIn", stay.checkIn())
			.setParameter("checkOut", stay.checkOut())
			.setParameter("nights", stay.nights())
			.setParameter("rooms", stay.rooms())
			.setParameter("adultsPerRoom", stay.adultsPerRoom())
			.setParameter("childrenPerRoom", stay.childrenPerRoom())
			.setParameter("now", filter.now())
			.setParameter("priceCurrency", filter.priceCurrency());
	}

}
