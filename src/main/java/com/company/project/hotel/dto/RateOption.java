package com.company.project.hotel.dto;

import com.company.project.common.dto.Money;

/**
 * One way of selling the room type: board, cancellation terms and price, for one room.
 *
 * @param boardType board code: RO, BB, HB, FB or AI
 * @param pricePerNight the stay's average night, rounded half up to the currency's smallest unit
 * @param totalPrice the whole stay, before any tax added on top and before discounts
 */
public record RateOption(Long ratePlanId, String name, String boardType, String boardTypeName,
		RateCancellation cancellation, Money pricePerNight, Money totalPrice) {
}
