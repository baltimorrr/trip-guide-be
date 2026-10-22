package com.company.project.search.dto;

import com.company.project.destination.model.DestinationType;

/**
 * A place matching the typed text.
 *
 * @param context the places it lies in, e.g. "Hải Châu, Đà Nẵng, Vietnam"; null for a country
 */
public record DestinationSuggestion(String slug, DestinationType type, String name, String context, int hotelCount) {
}
