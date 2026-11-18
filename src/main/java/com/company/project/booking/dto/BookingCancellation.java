package com.company.project.booking.dto;

import java.time.Instant;

import com.company.project.common.dto.Money;

/**
 * How a cancelled booking was cancelled (C-MYBK-03).
 *
 * @param penalty what the cancellation policy kept
 * @param refund null when nothing was refunded (a non-refundable rate, or nothing paid)
 */
public record BookingCancellation(Instant cancelledAt, String reason, Money penalty, RefundDetails refund) {
}
