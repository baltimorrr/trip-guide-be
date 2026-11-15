package com.company.project.discount.model;

/**
 * A code typed at checkout and the booking it would apply to.
 *
 * @param subtotalMinor the rooms' price before the discount, in minor units of {@code currency}
 * @param firstBooking true when the user has no other booking that is pending, confirmed, under way or done
 */
public record DiscountUse(String code, long userId, long hotelId, String currency, long subtotalMinor,
		boolean firstBooking) {
}
