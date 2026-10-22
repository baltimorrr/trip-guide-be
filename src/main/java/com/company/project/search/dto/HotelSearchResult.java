package com.company.project.search.dto;

import java.math.BigDecimal;

/** One hotel that can host the searched stay. Prices come with the result list story (C-SRCH-01). */
public record HotelSearchResult(Long id, String slug, String name, String accommodationType, Short starRating,
		String coverPhotoUrl, String district, String city, String countryCode, BigDecimal googleRating,
		Integer googleRatingCount, BigDecimal userRatingAvg, int userRatingCount, Integer distanceToCenterM) {
}
