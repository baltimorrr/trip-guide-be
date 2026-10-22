package com.company.project.destination.dto;

import com.company.project.destination.model.DestinationType;

/** A destination by slug, e.g. to label a search rebuilt from a shared URL or the browser's last search. */
public record DestinationResponse(String slug, DestinationType type, String name, String countryCode, int hotelCount,
		String imageUrl) {
}
