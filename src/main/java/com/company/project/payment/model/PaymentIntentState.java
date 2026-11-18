package com.company.project.payment.model;

import com.company.project.booking.model.PaymentStatus;

/**
 * Stripe's current state of a PaymentIntent, in this project's terms.
 *
 * @param amountMinor in minor units of {@code currency} (ISO 4217 digits)
 * @param currency upper-case ISO code
 * @param clientSecret what Stripe.js needs to confirm the payment; only the paying customer may see it
 * @param failureMessage Stripe's message for the customer when the last attempt was declined, else null
 * @param bookingReference from the intent's metadata
 */
public record PaymentIntentState(String id, PaymentStatus status, long amountMinor, String currency,
		String clientSecret, String failureMessage, String bookingReference) {

	@Override
	public String toString() {
		return "PaymentIntentState[id=" + id + ", status=" + status + ", amountMinor=" + amountMinor + ", currency="
				+ currency + ", clientSecret=***, failureMessage=" + failureMessage + ", bookingReference="
				+ bookingReference + "]";
	}

}
