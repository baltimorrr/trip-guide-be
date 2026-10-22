package com.company.project.search.dto;

/**
 * A listed hotel whose name matches the typed text.
 *
 * @param context district and city of the hotel
 */
public record HotelSuggestion(String slug, String name, String context) {
}
