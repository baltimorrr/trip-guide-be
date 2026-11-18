package com.company.project.booking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.company.project.booking.model.Booking;

/**
 * Hand-written booking queries, implemented in {@code impl/BookingRepositoryImpl}. A write that changes an existing
 * booking locks its row first (the {@code ForUpdate} methods), then the rows that hang off it (payments, refunds,
 * room inventory), so concurrent writes wait for each other instead of deadlocking.
 */
public interface BookingRepositoryCustom {

	/**
	 * Up to {@code limit} Pending bookings whose hold ended before {@code now}, oldest first, row-locked until the
	 * transaction ends. Bookings with an open payment are left out: their PaymentIntent is canceled on Stripe first, so
	 * a booking never expires while it can still be paid. Rows another transaction holds (a payment confirming that
	 * booking) are skipped, not waited for.
	 */
	List<Booking> findExpiredHoldsForUpdate(Instant now, int limit);

	/** The user's booking with this reference, row-locked until the transaction ends. */
	Optional<Booking> findByReferenceAndUserIdForUpdate(String reference, long userId);

	/** The booking, row-locked until the transaction ends. */
	Optional<Booking> findByIdForUpdate(long id);

	/**
	 * Why the status changes made in this transaction happen; the database's log of status changes records it
	 * ({@code app.status_reason}, read by the {@code bookings_status_log} triggers). Call it before changing them.
	 */
	void setStatusReason(String reason);

	/**
	 * Who makes the status changes in this transaction ({@code app.changed_by}); without it the log records the system
	 * for a change and the booking's user for a new booking. Call it before changing them.
	 */
	void setChangedBy(long userId);

}
