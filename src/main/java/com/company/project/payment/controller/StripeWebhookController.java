package com.company.project.payment.controller;

import com.company.project.payment.services.StripeWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe's webhook endpoint (C-PAY-01/02/05). Public, without a token: Stripe signs every call, and the signature is
 * checked over the exact body, which is why it is read as a String. Hidden from the API docs, as only Stripe calls it.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class StripeWebhookController {

	private final StripeWebhookService webhooks;

	/**
	 * 200 handled (or a repeat, or an event this API doesn't use); 400 a bad or missing signature; 500 or 503 when
	 * handling failed, so Stripe sends the event again.
	 */
	@PostMapping("/api/payments/stripe/webhook")
	public ResponseEntity<Void> receive(@RequestBody(required = false) String payload,
			@RequestHeader(value = "Stripe-Signature", required = false) String signature) {
		webhooks.handle(payload, signature);
		return ResponseEntity.ok().build();
	}

}
