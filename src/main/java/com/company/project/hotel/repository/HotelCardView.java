package com.company.project.hotel.repository;

import java.math.BigDecimal;

/** What a hotel card shows, read with {@link HotelJpql#CARD_SELECT}. */
public record HotelCardView(Long id, String slug, String name, String accommodationType, Short starRating,
		String coverPhotoUrl, String district, String city, String countryCode, BigDecimal googleRating,
		Integer googleRatingCount, BigDecimal userRatingAvg, Integer userRatingCount, Integer distanceToCenterM) {
}
