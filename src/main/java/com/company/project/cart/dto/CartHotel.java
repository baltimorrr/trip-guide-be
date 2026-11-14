package com.company.project.cart.dto;

/**
 * The hotel of a cart group, as its card shows it.
 *
 * @param listed false when the hotel is no longer on sale: its lines are then unavailable
 */
public record CartHotel(Long id, String slug, String name, Short starRating, String coverPhotoUrl, String city,
		String countryCode, boolean listed) {
}
