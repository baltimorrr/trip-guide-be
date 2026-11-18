package com.company.project.cart.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A line's new stay and number of rooms (C-CART-03). The rate plan and guests stay as they are; changing them is
 * removing the line and adding another.
 */
public record CartItemUpdateRequest(
		@NotNull LocalDate checkIn,
		@NotNull LocalDate checkOut,
		@Schema(example = "2") @NotNull @Min(1) Integer quantity) {
}
