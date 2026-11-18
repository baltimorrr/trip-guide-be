package com.company.project.payment.services;

import com.company.project.payment.model.WebhookEvent;

/** Checks that a webhook call comes from Stripe, by the signature Stripe computes over the exact request body. */
public interface StripeWebhookVerifier {

	/**
	 * The event in {@code payload}. BusinessException 400 when the signature is missing, wrong or too old, or the
	 * payload isn't a Stripe event; 503 when no webhook secret is configured.
	 */
	WebhookEvent verify(String payload, String signatureHeader);

}
