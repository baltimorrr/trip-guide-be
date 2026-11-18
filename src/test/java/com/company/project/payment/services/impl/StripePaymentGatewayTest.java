package com.company.project.payment.services.impl;

import static com.company.project.payment.services.impl.StripeSignatureVerifierTest.assertRefused;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.project.payment.config.StripeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Without a secret key nothing reaches Stripe: every call answers 503. Calls with a key are smoke-tested by hand. */
class StripePaymentGatewayTest {

	final StripePaymentGateway gateway = new StripePaymentGateway(new StripeProperties("", "whsec_x"));

	@Test
	void withoutASecretKeyCardPaymentsAreUnavailable() {
		assertRefused(() -> gateway.createPaymentIntent("HBTEST0001", 1_000_000, "VND"), HttpStatus.SERVICE_UNAVAILABLE);
		assertRefused(() -> gateway.retrievePaymentIntent("pi_1"), HttpStatus.SERVICE_UNAVAILABLE);
		assertRefused(() -> gateway.cancelPaymentIntent("pi_1"), HttpStatus.SERVICE_UNAVAILABLE);
		assertRefused(() -> gateway.createRefund("pi_1", 1_000, "VND", "HBTEST0001", 1), HttpStatus.SERVICE_UNAVAILABLE);
		assertRefused(() -> gateway.retrieveRefund("re_1"), HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	void propertiesNeverPrintTheSecrets() {
		assertThat(new StripeProperties("sk_test_abc", "whsec_def").toString())
			.doesNotContain("sk_test_abc")
			.doesNotContain("whsec_def");
	}

}
