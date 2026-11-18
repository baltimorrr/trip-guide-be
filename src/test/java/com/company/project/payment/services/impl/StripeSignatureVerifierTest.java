package com.company.project.payment.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import com.company.project.common.exception.BusinessException;
import com.company.project.payment.config.StripeProperties;
import com.company.project.payment.model.WebhookEvent;
import com.company.project.support.StripeWebhooks;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class StripeSignatureVerifierTest {

	final StripeSignatureVerifier verifier = new StripeSignatureVerifier(
			new StripeProperties(null, StripeWebhooks.SECRET), JsonMapper.builder().build());

	@Test
	void aSignedEventGivesItsIdTypeAndObject() {
		String payload = StripeWebhooks.event("evt_1", "payment_intent.succeeded", "pi_1");

		assertThat(verifier.verify(payload, StripeWebhooks.sign(payload)))
			.isEqualTo(new WebhookEvent("evt_1", "payment_intent.succeeded", "pi_1"));
	}

	@Test
	void aChangedBodyIsRefused() {
		String payload = StripeWebhooks.event("evt_2", "payment_intent.succeeded", "pi_2");
		String signature = StripeWebhooks.sign(payload);

		assertRefused(() -> verifier.verify(payload.replace("pi_2", "pi_3"), signature), HttpStatus.BAD_REQUEST);
	}

	@Test
	void aSignatureWithAnotherSecretIsRefused() {
		String payload = StripeWebhooks.event("payment_intent.succeeded", "pi_4");

		assertRefused(() -> verifier.verify(payload, StripeWebhooks.sign(payload, "whsec_other", Instant.now())),
				HttpStatus.BAD_REQUEST);
	}

	@Test
	void anOldSignatureIsRefused() {
		String payload = StripeWebhooks.event("payment_intent.succeeded", "pi_5");
		String signature = StripeWebhooks.sign(payload, StripeWebhooks.SECRET, Instant.now().minus(Duration.ofMinutes(10)));

		assertRefused(() -> verifier.verify(payload, signature), HttpStatus.BAD_REQUEST);
	}

	@Test
	void aMissingSignatureIsRefused() {
		String payload = StripeWebhooks.event("payment_intent.succeeded", "pi_6");

		assertRefused(() -> verifier.verify(payload, null), HttpStatus.BAD_REQUEST);
		assertRefused(() -> verifier.verify(payload, " "), HttpStatus.BAD_REQUEST);
	}

	@Test
	void aSignedBodyThatIsNotAnEventIsRefused() {
		String payload = "{\"hello\":\"world\"}";

		assertRefused(() -> verifier.verify(payload, StripeWebhooks.sign(payload)), HttpStatus.BAD_REQUEST);
	}

	@Test
	void withoutAWebhookSecretWebhooksAreUnavailable() {
		StripeSignatureVerifier unconfigured = new StripeSignatureVerifier(new StripeProperties(null, " "),
				JsonMapper.builder().build());
		String payload = StripeWebhooks.event("payment_intent.succeeded", "pi_7");

		assertRefused(() -> unconfigured.verify(payload, StripeWebhooks.sign(payload)), HttpStatus.SERVICE_UNAVAILABLE);
	}

	static void assertRefused(Runnable call, HttpStatus status) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
				ex -> assertThat(ex.getStatus()).isEqualTo(status));
	}

}
