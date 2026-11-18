package com.company.project.payment.services;

/**
 * Stripe's webhook events (C-PAY-01/02/05). Each one names a PaymentIntent or a refund; its current state is read back
 * from Stripe and applied, so repeated and out-of-order events end in Stripe's state. Every event is logged once by
 * its id.
 */
public interface StripeWebhookService {

	/**
	 * Verifies and handles one event. BusinessException 400 for a bad signature (nothing is logged or changed), 503
	 * when webhooks aren't set up. When handling fails the error is logged on the event and rethrown, so Stripe sends
	 * the event again.
	 */
	void handle(String payload, String signatureHeader);

}
