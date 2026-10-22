package com.company.project.destination.dto;

import java.math.BigDecimal;

/** One of the best-rated listed hotels shown under a popular destination. */
public record PopularHotel(Long id, String slug, String name, Short starRating, String coverPhotoUrl,
		BigDecimal userRatingAvg, int userRatingCount, String city) {
}
