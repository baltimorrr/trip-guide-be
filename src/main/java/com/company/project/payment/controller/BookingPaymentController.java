package com.company.project.payment.controller;

import com.company.project.payment.dto.BookingPayment;
import com.company.project.payment.dto.PaymentSession;
import com.company.project.payment.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paying a Pending booking by card (C-PAY-01/02). The client starts the payment, hands the client secret to Stripe's
 * Payment Element (card data and 3D Secure stay with Stripe.js), then polls the payment until Stripe's webhook has
 * confirmed the booking or the card was declined.
 */
@RestController
@RequestMapping("/api/bookings/{reference}/payment")
@RequiredArgsConstructor
public class BookingPaymentController {

	private final PaymentService paymentService;

	/**
	 * The PaymentIntent's client secret for the booking's total, the same one again while it can still be paid (also
	 * to retry after a declined card). A booking with nothing to pay comes back CONFIRMED without a secret. 404 a
	 * booking that isn't yours; 409 a booking that isn't Pending, whose hold has ended, or whose payment is being
	 * processed; 503 Stripe unavailable or not set up.
	 */
	@PostMapping
	public PaymentSession start(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
		return paymentService.start(userId(jwt), reference);
	}

	/**
	 * The booking's status and its payment as Stripe last reported it: SUCCEEDED once the booking is confirmed,
	 * FAILED with the decline message (retry until {@code payBy}). 404 a booking that isn't yours.
	 */
	@GetMapping
	public BookingPayment status(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
		return paymentService.status(userId(jwt), reference);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
