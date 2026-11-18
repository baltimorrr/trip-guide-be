package com.company.project.support;

import java.time.Instant;
import java.util.UUID;

import com.stripe.net.Webhook;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Webhook calls as Stripe sends them: minimal event JSON and the {@code Stripe-Signature} header computed with the
 * test profile's webhook secret ({@code app.stripe.webhook-secret} in application-test.yml).
 */
public final class StripeWebhooks {

	public static final String SECRET = "whsec_test_only_c2lnbmluZy1zZWNyZXQtZm9yLXRlc3Rz";

	private StripeWebhooks() {
	}

	/** An event of {@code type} about the object {@code objectId}, with a new event id. */
	public static String event(String type, String objectId) {
		return event("evt_test_" + UUID.randomUUID().toString().replace("-", ""), type, objectId);
	}

	public static String event(String eventId, String type, String objectId) {
		String object = type.startsWith("refund.") ? "refund" : "payment_intent";
		return """
				{"id":"%s","object":"event","api_version":"2026-10-28.endive","created":%d,"livemode":false,\
				"type":"%s","data":{"object":{"id":"%s","object":"%s"}}}"""
			.formatted(eventId, Instant.now().getEpochSecond(), type, objectId, object);
	}

	/** Sends {@code payload} to the webhook, signed as Stripe would. */
	public static MvcTestResult send(MockMvcTester mvc, String payload) {
		return mvc.post()
			.uri("/api/payments/stripe/webhook")
			.contentType(MediaType.APPLICATION_JSON)
			.header("Stripe-Signature", sign(payload))
			.content(payload)
			.exchange();
	}

	/** Sends a new event of {@code type} about {@code objectId}, signed. */
	public static MvcTestResult send(MockMvcTester mvc, String type, String objectId) {
		return send(mvc, event(type, objectId));
	}

	/** The signature header for {@code payload}, signed now with the test secret. */
	public static String sign(String payload) {
		return sign(payload, SECRET, Instant.now());
	}

	public static String sign(String payload, String secret, Instant at) {
		long timestamp = at.getEpochSecond();
		try {
			return "t=" + timestamp + ",v1=" + Webhook.Util.computeHmacSha256(secret, timestamp + "." + payload);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
