package com.company.project.booking.repository.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

import com.company.project.booking.repository.RoomInventoryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Native SQL: JPQL has no {@code insert ... on conflict do update} with a condition on the existing row, and no
 * {@code update ... from} with rows locked in a chosen order. The database's CHECK ({@code booked_rooms <=
 * total_rooms}) stays the last guard.
 */
@Repository
@RequiredArgsConstructor
public class RoomInventoryRepositoryImpl implements RoomInventoryRepository {

	// Nights are written in date order, so concurrent holds of one room type lock its rows in the same order. A
	// night where the rooms don't fit writes no row, and the caller compares the count with the nights.
	private static final String HOLD = """
			insert into room_inventory as i (room_type_id, stay_date, total_rooms, booked_rooms, updated_at)
			select rt.id, cast(n.night as date), rt.total_rooms, :rooms, now()
			from room_types rt
			cross join generate_series(cast(:checkIn as date), cast(:checkOut as date) - 1, interval '1 day') as n(night)
			where rt.id = :roomTypeId and :rooms <= rt.total_rooms
			order by n.night
			on conflict (room_type_id, stay_date) do update
			    set booked_rooms = i.booked_rooms + excluded.booked_rooms, updated_at = now()
			    where not i.is_closed and i.booked_rooms + excluded.booked_rooms <= i.total_rooms""";

	// Locks the rows in (room type, night) order first, as holds do, then subtracts each night's booked rooms.
	private static final String RELEASE = """
			with released as (
			    select r.room_type_id, n.stay_date, count(*) as rooms
			    from booking_rooms r
			    join booking_room_nights n on n.booking_room_id = r.id
			    where r.booking_id in (:bookingIds) and r.status = 'ACTIVE'
			    group by r.room_type_id, n.stay_date
			),
			locked as (
			    select i.room_type_id, i.stay_date
			    from room_inventory i
			    join released r on r.room_type_id = i.room_type_id and r.stay_date = i.stay_date
			    order by i.room_type_id, i.stay_date
			    for update of i
			)
			update room_inventory i set booked_rooms = i.booked_rooms - r.rooms, updated_at = now()
			from released r
			join locked l on l.room_type_id = r.room_type_id and l.stay_date = r.stay_date
			where i.room_type_id = r.room_type_id and i.stay_date = r.stay_date""";

	private final EntityManager entityManager;

	@Override
	public boolean hold(long roomTypeId, int rooms, LocalDate checkIn, LocalDate checkOut) {
		int written = entityManager.createNativeQuery(HOLD)
			.setParameter("roomTypeId", roomTypeId)
			.setParameter("rooms", rooms)
			.setParameter("checkIn", checkIn)
			.setParameter("checkOut", checkOut)
			.executeUpdate();
		return written == ChronoUnit.DAYS.between(checkIn, checkOut);
	}

	@Override
	public void release(Collection<Long> bookingIds) {
		entityManager.createNativeQuery(RELEASE).setParameter("bookingIds", bookingIds).executeUpdate();
	}

}
