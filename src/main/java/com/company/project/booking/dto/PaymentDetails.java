package com.company.project.booking.dto;

import java.time.Instant;

import com.company.project.booking.model.PaymentStatus;

/**
 * A booking's card payment.
 *
 * @param failureReason the bank's or Stripe's message for the customer when the last attempt was declined; the
 * customer can retry until the booking's hold ends
 * @param paidAt set when SUCCEEDED
 */
public record PaymentDetails(PaymentStatus status, String failureReason, Instant paidAt) {
}
