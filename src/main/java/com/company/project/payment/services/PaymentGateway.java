package com.company.project.payment.services;

import com.company.project.payment.model.PaymentIntentState;
import com.company.project.payment.model.RefundState;

/**
 * Calls to Stripe's API. Every method throws {@link PaymentGatewayException} (503) when Stripe can't be reached or
 * answers with an error, and a {@code BusinessException} 503 when no secret key is configured. Card data never passes
 * through here: the customer's browser sends it to Stripe with the intent's client secret.
 */
public interface PaymentGateway {

	/**
	 * A card PaymentIntent for the booking's total. Idempotent per booking: calling it again for the same reference
	 * returns the same intent (within Stripe's 24 hours). BusinessException 409 when the amount is below Stripe's
	 * minimum charge.
	 */
	PaymentIntentState createPaymentIntent(String bookingReference, long amountMinor, String currency);

	PaymentIntentState retrievePaymentIntent(String id);

	/**
	 * Cancels the intent so it can never charge. An intent that already succeeded or was already canceled can't be
	 * canceled: its current state is returned instead.
	 */
	PaymentIntentState cancelPaymentIntent(String id);

	/**
	 * Refunds {@code amountMinor} of the intent's charge to the customer's card. Idempotent per booking (one
	 * cancellation, one refund).
	 *
	 * @param refundId the {@code refunds} row, stored in the refund's metadata
	 */
	RefundState createRefund(String paymentIntentId, long amountMinor, String currency, String bookingReference,
			long refundId);

	RefundState retrieveRefund(String id);

}
