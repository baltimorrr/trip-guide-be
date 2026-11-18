package com.company.project.payment.dto;

import java.time.Instant;

import com.company.project.booking.dto.PaymentDetails;
import com.company.project.booking.model.BookingStatus;
import com.company.project.common.dto.Money;

/**
 * Where a booking's payment is, for the page that waits for Stripe's confirmation or shows a declined card.
 *
 * @param payBy while the booking is Pending, until when the customer can pay (or retry); null otherwise
 * @param payment null until payment starts
 */
public record BookingPayment(String reference, BookingStatus bookingStatus, Instant payBy, Money amount,
		PaymentDetails payment) {
}
