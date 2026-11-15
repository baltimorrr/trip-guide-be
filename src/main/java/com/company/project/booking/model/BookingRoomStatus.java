package com.company.project.booking.model;

/** A booked room stays ACTIVE unless it alone is cancelled (C-MYBK-03 decides whether that is allowed). */
public enum BookingRoomStatus {
	ACTIVE,
	CANCELLED
}
