package com.company.project.hotel.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The hotel page without dates (C-HOTEL-01/02/03): what the hotel is, where it is, what it offers and its rules.
 * Rooms and prices depend on the stay and come from {@code /rooms}; reviews from {@code /reviews}.
 *
 * @param accommodationType code: hotel, resort, apartment, villa or hostel
 * @param distanceToCenterM metres from the city centre, null when not known
 * @param photos the gallery: hotel photos (cover first), then room photos
 * @param facilities grouped under their headings, alphabetical
 */
public record HotelDetail(Long id, String slug, String name, String accommodationType, String accommodationTypeName,
		Short starRating, String description, HotelAddress address, Integer distanceToCenterM, BigDecimal googleRating,
		Integer googleRatingCount, BigDecimal userRatingAvg, int userRatingCount, List<GalleryPhoto> photos,
		List<FacilityGroup> facilities, HotelPolicies policies) {
}
