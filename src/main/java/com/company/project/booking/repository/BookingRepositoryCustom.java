package com.company.project.booking.repository;

import java.time.Instant;
import java.util.List;

import com.company.project.booking.model.Booking;

/** Hand-written booking queries, implemented in {@code impl/BookingRepositoryImpl}. */
public interface BookingRepositoryCustom {

	/**
	 * Up to {@code limit} Pending bookings whose hold ended before {@code now}, oldest first, row-locked until the
	 * transaction ends. Rows another transaction holds (a payment confirming that booking) are skipped, not waited
	 * for.
	 */
	List<Booking> findExpiredHoldsForUpdate(Instant now, int limit);

	/**
	 * Why the status changes made in this transaction happen; the database's log of status changes records it
	 * ({@code app.status_reason}, read by the {@code bookings_status_log} triggers). Call it before changing them.
	 */
	void setStatusReason(String reason);

}
