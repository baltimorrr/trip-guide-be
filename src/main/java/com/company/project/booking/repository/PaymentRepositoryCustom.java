package com.company.project.booking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.company.project.booking.model.Payment;

/** Hand-written payment queries, implemented in {@code impl/PaymentRepositoryImpl}. */
public interface PaymentRepositoryCustom {

	/**
	 * The booking a PaymentIntent pays for, without loading the payment: a write locks the booking row first and
	 * reads its payment after that, so it never works on a copy another transaction just changed.
	 */
	Optional<Long> findBookingIdByPaymentIntentId(String paymentIntentId);

	/**
	 * Up to {@code limit} open payments of Pending bookings whose hold ended before {@code now}, oldest hold first,
	 * without locks: their PaymentIntents are canceled on Stripe before the booking may expire.
	 */
	List<Payment> findOpenOfExpiredHolds(Instant now, int limit);

}
