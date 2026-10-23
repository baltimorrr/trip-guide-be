package com.company.project.search.dto;

import com.company.project.common.dto.Money;

/**
 * {@code quantity} rooms of one room type sold on one rate plan.
 *
 * @param boardType board code: RO, BB, HB, FB or AI
 * @param price these rooms for the whole stay
 */
public record OfferRoom(Long roomTypeId, String roomTypeName, Long ratePlanId, String ratePlanName, int quantity,
		String boardType, String boardTypeName, CancellationTerms cancellation, Money price) {
}
