package com.company.project.payment.model;

import com.company.project.booking.model.RefundStatus;

/**
 * Stripe's current state of a refund, in this project's terms.
 *
 * @param refundId the {@code refunds} row it was created for (its metadata), or null when another system created it
 * @param failureReason Stripe's reason when it failed, else null
 */
public record RefundState(String id, RefundStatus status, Long refundId, String failureReason) {
}
