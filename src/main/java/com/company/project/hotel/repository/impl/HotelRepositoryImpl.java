package com.company.project.hotel.repository.impl;

import java.util.Collection;
import java.util.List;

import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelJpql;
import com.company.project.hotel.repository.HotelRepositoryCustom;
import com.company.project.hotel.repository.HotelSql;
import com.company.project.hotel.repository.HotelSuggestionView;
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
 * JPQL everywhere except the availability check. That one is native SQL because it needs one row per night of
 * the stay ({@code generate_series}), where a night without an inventory row counts as the room type's default
 * capacity, then a minimum per room type and a sum per hotel. It returns ids only; JPQL loads the cards.
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
	 * Candidate hotels whose fitting room types together have at least {@code :rooms} rooms free on every night
	 * of {@code [:checkIn, :checkOut)}. Children may also use places adults leave free.
	 */
	private static final String AVAILABILITY = """
			nights as (
			    select cast(generate_series(cast(:checkIn as date), cast(:checkOut as date) - 1, interval '1 day') as date)
			           as stay_date
			),
			free_rooms as (
			    select rt.hotel_id,
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
			available as (
			    select hotel_id from free_rooms group by hotel_id having sum(greatest(free, 0)) >= :rooms
			)
			""";

	/** Best user rating first; the id makes the order stable across pages. */
	private static final String AVAILABLE_IDS = """
			select h.id
			from hotels h
			join available v on v.hotel_id = h.id
			order by h.user_rating_avg desc nulls last, h.user_rating_count desc, h.id""";

	private static final String COUNT_AVAILABLE = "select count(*) from available";

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
	public Page<Long> findAvailableInDestination(long destinationId, StayRequirement stay, Pageable pageable) {
		return availableIds(CANDIDATES_IN_DESTINATION, destinationId, stay, pageable);
	}

	@Override
	public Page<Long> findAvailableHotel(long hotelId, StayRequirement stay, Pageable pageable) {
		return availableIds(CANDIDATE_HOTEL, hotelId, stay, pageable);
	}

	@SuppressWarnings("unchecked") // a native query typed with Long.class returns Long rows
	private Page<Long> availableIds(String candidates, long locationId, StayRequirement stay, Pageable pageable) {
		List<Long> ids = bind(entityManager.createNativeQuery(candidates + AVAILABILITY + AVAILABLE_IDS, Long.class),
				locationId, stay)
			.setFirstResult((int) pageable.getOffset())
			.setMaxResults(pageable.getPageSize())
			.getResultList();
		// Counts only when the page alone cannot tell the total.
		return PageableExecutionUtils.getPage(ids, pageable,
				() -> (Long) bind(entityManager.createNativeQuery(candidates + AVAILABILITY + COUNT_AVAILABLE,
						Long.class), locationId, stay).getSingleResult());
	}

	private static Query bind(Query query, long locationId, StayRequirement stay) {
		return query.setParameter("locationId", locationId)
			.setParameter("checkIn", stay.checkIn())
			.setParameter("checkOut", stay.checkOut())
			.setParameter("rooms", stay.rooms())
			.setParameter("adultsPerRoom", stay.adultsPerRoom())
			.setParameter("childrenPerRoom", stay.childrenPerRoom());
	}

}
