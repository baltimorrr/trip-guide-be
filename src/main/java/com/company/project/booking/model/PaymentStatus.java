package com.company.project.booking.model;

import java.util.Set;

/**
 * Where a booking's card payment is, as Stripe last reported its PaymentIntent. SUCCEEDED and CANCELED are final; the
 * others are open, so the customer can still complete the payment.
 */
public enum PaymentStatus {
	/** Waiting for the customer's card (or for 3D Secure). */
	REQUIRES_PAYMENT,
	/** Stripe is still processing the charge. */
	PROCESSING,
	/** Paid; the booking is confirmed (or, if it no longer was Pending, needs a manual refund). */
	SUCCEEDED,
	/** The last attempt was declined; the customer can retry on the same intent while the hold lasts. */
	FAILED,
	/** Canceled, usually because the hold ended; it can never charge. */
	CANCELED;

	/** The statuses of a payment the customer can still complete. */
	public static final Set<PaymentStatus> OPEN = Set.of(REQUIRES_PAYMENT, PROCESSING, FAILED);

	public boolean isFinal() {
		return this == SUCCEEDED || this == CANCELED;
	}

}
