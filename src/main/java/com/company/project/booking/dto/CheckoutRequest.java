package com.company.project.booking.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The cart lines to check out, all of one hotel and one date range (one cart group), and an optional discount code.
 */
public record CheckoutRequest(@NotEmpty @Size(max = 20) List<@NotNull Long> cartItemIds,
		@Size(max = 50) String discountCode) {
}
