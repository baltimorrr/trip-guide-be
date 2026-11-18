package com.company.project.booking.model;

/**
 * A booking was just cancelled. Published inside the cancelling transaction; the refund is sent to Stripe and the
 * emails go out after it commits.
 *
 * @param refundId the refund to send, or null when nothing is refunded
 */
public record BookingCancelled(long bookingId, Long refundId) {
}
