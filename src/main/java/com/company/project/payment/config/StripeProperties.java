package com.company.project.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe settings under {@code app.stripe}. Both are optional so the app runs without a Stripe account: without the
 * secret key the payment endpoints answer 503, without the webhook secret the webhook does.
 *
 * @param secretKey the API secret key ({@code sk_test_…} or {@code sk_live_…})
 * @param webhookSecret the webhook endpoint's signing secret ({@code whsec_…}); {@code stripe listen} prints one for
 * local development
 */
@ConfigurationProperties("app.stripe")
public record StripeProperties(String secretKey, String webhookSecret) {

	public boolean hasSecretKey() {
		return secretKey != null && !secretKey.isBlank();
	}

	public boolean hasWebhookSecret() {
		return webhookSecret != null && !webhookSecret.isBlank();
	}

	@Override
	public String toString() {
		return "StripeProperties[secretKey=" + (hasSecretKey() ? "***" : "") + ", webhookSecret="
				+ (hasWebhookSecret() ? "***" : "") + "]";
	}

}
