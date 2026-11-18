package com.company.project.booking.dto;

import com.company.project.common.dto.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cancels the whole booking. In Swagger, replace {@code expectedRefund} with the {@code refund} of
 * {@code GET .../cancellation}; any other amount answers 409.
 *
 * @param expectedRefund the cancellation preview's {@code refund}, which the customer agreed to
 * @param reason optional, kept with the booking and its status log
 */
public record CancelBookingRequest(
		@Schema(description = "the cancellation preview's refund") @NotNull @Valid Money expectedRefund,
		@Schema(example = "My plans changed.") @Size(max = 450) String reason) {
}
