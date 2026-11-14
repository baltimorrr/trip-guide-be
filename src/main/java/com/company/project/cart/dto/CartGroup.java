package com.company.project.cart.dto;

import java.time.LocalDate;
import java.util.List;

import com.company.project.common.dto.Money;

/**
 * The cart's lines for one hotel and one date range: what one checkout books as one booking.
 *
 * @param taxesIncluded true when the hotel's country adds no tax on top of room prices for these nights
 * @param canCheckout every line is {@code AVAILABLE}
 * @param total the {@code AVAILABLE} lines' total prices; null when there is none
 * @param items in the order they were added
 */
public record CartGroup(CartHotel hotel, LocalDate checkIn, LocalDate checkOut, int nights, boolean taxesIncluded,
		boolean canCheckout, Money total, List<CartItemResponse> items) {
}
