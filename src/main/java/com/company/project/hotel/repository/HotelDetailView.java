package com.company.project.hotel.repository;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * What the hotel page shows about the hotel itself, with its address parts and check-in/out times.
 *
 * @param countryName the COUNTRY destination's current name; null before the destination sync has created it
 */
public record HotelDetailView(Long id, String slug, String name, String accommodationType,
		String accommodationTypeName, Short starRating, String description, String line1, String ward, String district,
		String city, String countryCode, String countryName, BigDecimal latitude, BigDecimal longitude,
		Integer distanceToCenterM, BigDecimal googleRating, Integer googleRatingCount, BigDecimal userRatingAvg,
		Integer userRatingCount, LocalTime checkInFrom, LocalTime checkInUntil, LocalTime checkOutUntil,
		Short minCheckInAge) {
}
