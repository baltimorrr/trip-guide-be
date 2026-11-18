package com.company.project.payment.dto;

import java.time.Instant;

import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.model.PaymentStatus;
import com.company.project.common.dto.Money;

/**
 * What the client needs to take the card with Stripe's Payment Element: the PaymentIntent's client secret (pass it to
 * Stripe.js with the publishable key), the amount and until when the rooms are held. After Stripe.js returns, poll
 * {@code GET /api/bookings/{reference}/payment}: only Stripe's webhook confirms the booking.
 *
 * @param status the booking's status: CONFIRMED right away when there was nothing to pay
 * @param clientSecret null when there is nothing to pay
 * @param paymentStatus null when there is nothing to pay
 * @param payBy the end of the hold; after it the payment is canceled and the booking expires
 */
public record PaymentSession(String reference, BookingStatus status, Money amount, String clientSecret,
		PaymentStatus paymentStatus, Instant payBy) {

	@Override
	public String toString() {
		return "PaymentSession[reference=" + reference + ", status=" + status + ", amount=" + amount
				+ ", clientSecret=***, paymentStatus=" + paymentStatus + ", payBy=" + payBy + "]";
	}

}
