package com.company.project.cart.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Rooms to put in the cart: {@code quantity} rooms of one rate plan for the stay, each holding {@code adults} and
 * children of {@code childAges}. The stay and the totals are checked by the search's rules.
 *
 * @param adults per room
 * @param childAges per room, one age per child
 */
public record CartItemRequest(
		@NotNull Long ratePlanId,
		@NotNull LocalDate checkIn,
		@NotNull LocalDate checkOut,
		@NotNull @Min(1) Integer quantity,
		@NotNull @Min(1) Integer adults,
		List<@NotNull @Min(0) @Max(17) Integer> childAges) {

	public CartItemRequest {
		childAges = childAges == null ? List.of() : childAges;
	}

}
