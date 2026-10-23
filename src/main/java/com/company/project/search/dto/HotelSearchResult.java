package com.company.project.search.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One hotel that can host the searched stay, with its cheapest offer.
 *
 * @param photoUrls photos of the hotel itself, cover first
 */
public record HotelSearchResult(Long id, String slug, String name, String accommodationType, Short starRating,
		List<String> photoUrls, String district, String city, String countryCode, BigDecimal googleRating,
		Integer googleRatingCount, BigDecimal userRatingAvg, int userRatingCount, Integer distanceToCenterM,
		StayOffer offer) {
}
