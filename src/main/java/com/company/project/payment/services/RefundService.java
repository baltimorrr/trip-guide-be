package com.company.project.payment.services;

import com.company.project.payment.model.RefundState;

/**
 * Refunds to the customer's card through Stripe (C-PAY-05). A refund row is written by the cancellation; this sends it
 * to Stripe after that commits, resends the ones that didn't get there, and keeps their status in step with Stripe's.
 */
public interface RefundService {

	/**
	 * Sends the refund to Stripe (idempotent per booking). When Stripe can't be reached it stays unsent for
	 * {@link #retryUnsent}; it never throws for that.
	 */
	void send(long refundId);

	/**
	 * Resends up to 100 refunds that didn't reach Stripe within a minute. One still unsent after 23 hours is marked
	 * FAILED and logged instead: Stripe forgets idempotency keys after 24, so a resend could refund twice.
	 *
	 * @return how many were sent or given up on
	 */
	int retryUnsent();

	/** Takes Stripe's current state of a refund; one this API didn't create is ignored. */
	void applyRefund(RefundState state);

}
