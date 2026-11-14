package com.company.project.cart.dto;

import java.util.List;

import com.company.project.common.dto.Money;
import com.company.project.hotel.dto.RateCancellation;

/**
 * One cart line, priced now. Prices are for the whole stay, in the hotel's currency, before any tax added on top.
 * {@code roomsLeft}, {@code cancellation}, {@code pricePerRoom} and {@code totalPrice} are null when the line can't
 * be priced ({@code UNAVAILABLE}, {@code CHECK_IN_PASSED}).
 *
 * @param boardType board code: RO, BB, HB, FB or AI
 * @param adults per room
 * @param childAges per room
 * @param roomsLeft rooms of this type free on every night, shared by the group's lines of this room type
 * @param cancellation what cancelling one room costs, and when
 * @param pricePerRoom one room for the stay, now
 * @param totalPrice {@code pricePerRoom} times {@code quantity}
 * @param priceAtAdd one room for the stay when the line was last picked: added, added again or given new dates
 * @param priceChanged whether {@code pricePerRoom} differs from {@code priceAtAdd}
 */
public record CartItemResponse(Long id, Long ratePlanId, String ratePlanName, Long roomTypeId, String roomTypeName,
		String photoUrl, String boardType, String boardTypeName, int quantity, int adults, List<Integer> childAges,
		CartItemStatus status, Integer roomsLeft, RateCancellation cancellation, Money pricePerRoom, Money totalPrice,
		Money priceAtAdd, boolean priceChanged) {
}
