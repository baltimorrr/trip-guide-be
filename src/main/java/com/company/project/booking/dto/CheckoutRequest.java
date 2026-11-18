package com.company.project.booking.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The cart lines to check out, all of one hotel and one date range (one cart group), and an optional discount code.
 * The Swagger examples are the first cart line of a new database and the demo seed's code.
 */
public record CheckoutRequest(
		@Schema(example = "[1]", description = "line ids from GET /api/cart")
		@NotEmpty @Size(max = 20) List<@NotNull Long> cartItemIds,
		@Schema(example = "welcome10") @Size(max = 50) String discountCode) {
}
