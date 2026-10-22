package com.company.project.destination.dto;

import java.util.List;

import com.company.project.destination.model.DestinationType;

/**
 * A homepage destination. Clicking it runs the default search, built by the client because only it
 * knows the traveller's "tomorrow": {@code /api/search/hotels?destination={slug}&checkIn={tomorrow}
 * &checkOut={day after}&rooms=1&adults=2}.
 */
public record PopularDestinationResponse(String slug, DestinationType type, String name, String imageUrl,
		int hotelCount, List<PopularHotel> hotels) {
}
