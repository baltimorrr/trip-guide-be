package com.company.project.booking.dto;

import java.util.List;

import com.company.project.common.dto.Money;
import com.company.project.hotel.dto.RateCancellation;

/**
 * One cart line at checkout: {@code quantity} rooms of one rate, each holding the same guests.
 *
 * @param pricePerRoom one room for the whole stay (the sum of its nights)
 * @param subtotal every room of the line: nights × rate × quantity
 * @param cancellation the rate's cancellation terms, which the booker accepts
 */
public record QuoteLine(Long cartItemId, Long ratePlanId, String ratePlanName, Long roomTypeId, String roomTypeName,
		String boardType, String boardTypeName, int quantity, int adults, List<Integer> childAges, Money pricePerRoom,
		Money subtotal, RateCancellation cancellation) {
}
