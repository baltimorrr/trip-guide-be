package com.company.project.destination.repository;

import com.company.project.destination.model.DestinationType;

/**
 * A destination matching typed text. {@code district} and {@code city} are the place's own spellings (null below
 * its level) and {@code countryName} is the name of its country's row; the mapper turns them into the label.
 */
public record DestinationSuggestionView(String slug, DestinationType type, String name, Integer hotelCount,
		String district, String city, String countryName) {
}
