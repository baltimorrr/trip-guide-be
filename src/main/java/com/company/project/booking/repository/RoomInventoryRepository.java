package com.company.project.booking.repository;

import java.time.LocalDate;
import java.util.Collection;

/**
 * The rooms bookings take from {@code room_inventory}, implemented in {@code impl/RoomInventoryRepositoryImpl}.
 * {@code booked_rooms} counts Pending and Confirmed bookings, so search, the hotel page and the cart stop offering
 * held rooms by themselves. The table has no entity (only set-based SQL writes it), so this is a plain interface.
 * Every method runs inside the caller's transaction.
 */
public interface RoomInventoryRepository {

	/**
	 * Adds {@code rooms} to {@code booked_rooms} of the room type for every night of {@code [checkIn, checkOut)},
	 * creating a night's row from the room type's total when there is none, but only where they still fit and the
	 * night isn't closed. The rows stay locked until the transaction ends, so two bookings can never both take the
	 * last room. Call it for a booking's room types in id order, so concurrent bookings lock rows in the same order.
	 *
	 * @return false when some night had no room for them; roll the transaction back then, as other nights were taken
	 */
	boolean hold(long roomTypeId, int rooms, LocalDate checkIn, LocalDate checkOut);

	/**
	 * Gives back the rooms these bookings' active rooms hold, night by night: the twin of {@link #hold}, for a
	 * booking whose hold ended (and later a cancellation). Rows are locked in the same order as {@link #hold}.
	 */
	void release(Collection<Long> bookingIds);

}
