package com.company.project.payment.services.impl;

import com.company.project.common.exception.BusinessException;
import com.company.project.payment.config.StripeProperties;
import com.company.project.payment.model.WebhookEvent;
import com.company.project.payment.services.StripeWebhookVerifier;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the {@code Stripe-Signature} header with stripe-java (HMAC-SHA256 over the timestamp and body, at most 5
 * minutes old), then reads the event's id, type and object id from the JSON itself, so the result doesn't depend on
 * the API version the webhook endpoint is set to.
 */
@Service
@RequiredArgsConstructor
class StripeSignatureVerifier implements StripeWebhookVerifier {

	private final StripeProperties properties;

	private final JsonMapper jsonMapper;

	@Override
	public WebhookEvent verify(String payload, String signatureHeader) {
		if (!properties.hasWebhookSecret()) {
			throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhooks aren't set up on this server");
		}
		if (payload == null || signatureHeader == null || signatureHeader.isBlank()) {
			throw invalidSignature();
		}
		try {
			Webhook.Signature.verifyHeader(payload, signatureHeader, properties.webhookSecret(),
					Webhook.DEFAULT_TOLERANCE);
		}
		catch (SignatureVerificationException ex) {
			throw invalidSignature();
		}
		JsonNode event;
		try {
			event = jsonMapper.readTree(payload);
		}
		catch (JacksonException ex) {
			throw invalidEvent();
		}
		String id = text(event.path("id"));
		String type = text(event.path("type"));
		if (id == null || type == null) {
			throw invalidEvent();
		}
		return new WebhookEvent(id, type, text(event.path("data").path("object").path("id")));
	}

	private static String text(JsonNode node) {
		return node.isString() && !node.asString().isBlank() ? node.asString() : null;
	}

	private static BusinessException invalidSignature() {
		return new BusinessException(HttpStatus.BAD_REQUEST, "Invalid Stripe signature");
	}

	private static BusinessException invalidEvent() {
		return new BusinessException(HttpStatus.BAD_REQUEST, "Not a Stripe event");
	}

}
