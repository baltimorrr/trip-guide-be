package com.company.project.cart.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Rooms to put in the cart: {@code quantity} rooms of one rate plan for the stay, each holding {@code adults} and
 * children of {@code childAges}. The stay and the totals are checked by the search's rules. The Swagger example
 * dates come from {@code OpenApiExamples}.
 *
 * @param ratePlanId a rate's {@code ratePlanId} from the hotel page's rooms
 * @param adults per room
 * @param childAges per room, one age per child
 */
public record CartItemRequest(
		@Schema(example = "9", description = "a rate's ratePlanId from GET /api/hotels/{slug}/rooms; "
				+ "on a new database, 9 is Mỹ Khê's Deluxe Double, room only")
		@NotNull Long ratePlanId,
		@NotNull LocalDate checkIn,
		@NotNull LocalDate checkOut,
		@Schema(example = "1") @NotNull @Min(1) Integer quantity,
		@Schema(example = "2") @NotNull @Min(1) Integer adults,
		@Schema(example = "[]") List<@NotNull @Min(0) @Max(17) Integer> childAges) {

	public CartItemRequest {
		childAges = childAges == null ? List.of() : childAges;
	}

}
