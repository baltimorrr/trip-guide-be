package com.company.project.wishlist.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A saved hotel, with the same card fields as a search result (one cover photo instead of the carousel).
 *
 * @param available false once the hotel is no longer listed: it can't be opened or booked until it is again
 * @param savedAt when the hotel was first saved
 */
public record WishlistHotel(Long id, String slug, String name, String accommodationType, Short starRating,
		String coverPhotoUrl, String district, String city, String countryCode, BigDecimal googleRating,
		Integer googleRatingCount, BigDecimal userRatingAvg, int userRatingCount, Integer distanceToCenterM,
		boolean available, Instant savedAt) {
}
