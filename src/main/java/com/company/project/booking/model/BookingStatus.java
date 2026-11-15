package com.company.project.booking.model;

/**
 * Where a booking is in its lifecycle. The database refuses any change that is not one of these arrows
 * ({@code booking_status_check()} in V10): PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT, PENDING → CANCELLED or
 * EXPIRED, CONFIRMED → CANCELLED or NO_SHOW.
 */
public enum BookingStatus {
	/** Created, rooms held until {@code holdExpiresAt}, waiting for payment. */
	PENDING,
	/** Paid; shown to customers as "Success". */
	CONFIRMED,
	CHECKED_IN,
	CHECKED_OUT,
	CANCELLED,
	/** The hold ran out before payment; the rooms went back on sale. */
	EXPIRED,
	NO_SHOW
}
