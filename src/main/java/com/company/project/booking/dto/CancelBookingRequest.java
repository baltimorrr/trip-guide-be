package com.company.project.booking.dto;

import com.company.project.common.dto.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cancels the whole booking.
 *
 * @param expectedRefund the cancellation preview's {@code refund}, which the customer agreed to
 * @param reason optional, kept with the booking and its status log
 */
public record CancelBookingRequest(@NotNull @Valid Money expectedRefund, @Size(max = 450) String reason) {
}
