package com.company.project.hotel.repository.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelJpql;
import com.company.project.hotel.repository.HotelOfferView;
import com.company.project.hotel.repository.HotelPhotoView;
import com.company.project.hotel.repository.HotelRepositoryCustom;
import com.company.project.hotel.repository.HotelSql;
import com.company.project.hotel.repository.HotelSuggestionView;
import com.company.project.hotel.repository.OfferLineView;
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
 * ({@code distinct on}); and a running sum of free rooms to fill the requested rooms from the cheapest room type up.
 * It returns ids, quantities and prices only; JPQL loads the cards and the names.
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

	private static final String TOP_IN_DESTINATION = HotelJpql.CARD_SELECT
			+ "join Destination d on d.id = :destinationId\nwhere " + HotelJpql.HOTEL_LISTED
			+ "\n  and " + HotelJpql.ADDRESS_IN_DESTINATION
			+ "\norder by h.userRatingAvg desc nulls last, h.googleRating desc nulls last, h.id";

	private static final String CANDIDATES_IN_DESTINATION = """
			with candidate_hotels as (
			    select h.id
			    from hotels h
			    join addresses a on a.id = h.address_id
			    join destinations d on d.id = :locationId
			    where\s""" + HotelSql.HOTEL_LISTED + " and " + HotelSql.ADDRESS_IN_DESTINATION + "\n),\n";

	private static final String CANDIDATE_HOTEL = """
			with candidate_hotels as (
			    select h.id from hotels h where h.id = :locationId and\s""" + HotelSql.HOTEL_LISTED + "\n),\n";

	/**
	 * Candidate hotels that can sell {@code :rooms} rooms for every night of {@code [:checkIn, :checkOut)}, and how.
	 * <ul>
	 * <li>{@code free_rooms}: fitting room types and their fewest free rooms on any night. Children may also use
	 * places adults leave free.</li>
	 * <li>{@code rate_offers}: their active rate plans in the hotel's currency, priced for the stay, without a closed
	 * night and with the stay at least as long as the longest minimum stay of its nights.</li>
	 * <li>{@code cheapest_rates}: the cheapest of those per room type (ties: lowest rate plan id).</li>
	 * <li>{@code picked}: rooms taken from the cheapest room type up (ties: lowest room type id) until
	 * {@code :rooms} are covered; {@code available}: the hotels where they are.</li>
	 * </ul>
	 */
	private static final String PRICED_AVAILABILITY = """
			nights as (
			    select cast(generate_series(cast(:checkIn as date), cast(:checkOut as date) - 1, interval '1 day') as date)
			           as stay_date
			),
			free_rooms as (
			    select rt.hotel_id, rt.id as room_type_id,
			           min(case when coalesce(i.is_closed, false) then 0
			                    else coalesce(i.total_rooms, rt.total_rooms) - coalesce(i.booked_rooms, 0) end) as free
			    from room_types rt
			    join candidate_hotels c on c.id = rt.hotel_id
			    cross join nights n
			    left join room_inventory i on i.room_type_id = rt.id and i.stay_date = n.stay_date
			    where rt.status = 'ACTIVE' and rt.deleted_at is null
			      and rt.max_adults >= :adultsPerRoom
			      and rt.max_occupancy >= :adultsPerRoom + :childrenPerRoom
			      and :childrenPerRoom <= rt.max_children + (rt.max_adults - :adultsPerRoom)
			    group by rt.hotel_id, rt.id
			),
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
			),
			cheapest_rates as (
			    select distinct on (room_type_id) hotel_id, room_type_id, free, rate_plan_id, currency, stay_price
			    from rate_offers
			    order by room_type_id, stay_price, rate_plan_id
			),
			filled as (
			    select c.*, sum(free) over (partition by hotel_id order by stay_price, room_type_id
			                                rows unbounded preceding) - free as rooms_before
			    from cheapest_rates c
			),
			picked as (
			    select hotel_id, room_type_id, rate_plan_id, currency, stay_price,
			           least(free, :rooms - rooms_before) as quantity
			    from filled
			    where rooms_before < :rooms
			),
			available as (
			    select hotel_id from picked group by hotel_id having sum(quantity) >= :rooms
			)
			""";

	/**
	 * The offer lines of one page of available hotels: best user rating first, the id making the order stable
	 * across pages; within a hotel, cheapest line first.
	 */
	private static final String PAGE_LINES = """
			, page as (
			    select h.id,
			           row_number() over (order by h.user_rating_avg desc nulls last, h.user_rating_count desc, h.id)
			           as position
			    from hotels h
			    join available v on v.hotel_id = h.id
			    order by position
			    limit :limit offset :offset
			)
			select p.hotel_id, p.room_type_id, p.rate_plan_id, p.quantity, p.stay_price, p.currency
			from picked p
			join page on page.id = p.hotel_id
			order by page.position, p.stay_price, p.room_type_id""";

	private static final String COUNT_AVAILABLE = "select count(*) from available";

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
	public Page<HotelOfferView> findAvailableInDestination(long destinationId, StayRequirement stay,
			Pageable pageable) {
		return availableOffers(CANDIDATES_IN_DESTINATION, destinationId, stay, pageable);
	}

	@Override
	public Page<HotelOfferView> findAvailableHotel(long hotelId, StayRequirement stay, Pageable pageable) {
		return availableOffers(CANDIDATE_HOTEL, hotelId, stay, pageable);
	}

	@SuppressWarnings("unchecked") // a native query without a result class returns Object[] rows
	private Page<HotelOfferView> availableOffers(String candidates, long locationId, StayRequirement stay,
			Pageable pageable) {
		List<Object[]> rows = bind(entityManager.createNativeQuery(candidates + PRICED_AVAILABILITY + PAGE_LINES),
				locationId, stay)
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
				() -> ((Number) bind(entityManager.createNativeQuery(candidates + PRICED_AVAILABILITY + COUNT_AVAILABLE),
						locationId, stay).getSingleResult()).longValue());
	}

	private static Query bind(Query query, long locationId, StayRequirement stay) {
		return query.setParameter("locationId", locationId)
			.setParameter("checkIn", stay.checkIn())
			.setParameter("checkOut", stay.checkOut())
			.setParameter("nights", stay.nights())
			.setParameter("rooms", stay.rooms())
			.setParameter("adultsPerRoom", stay.adultsPerRoom())
			.setParameter("childrenPerRoom", stay.childrenPerRoom());
	}

}
