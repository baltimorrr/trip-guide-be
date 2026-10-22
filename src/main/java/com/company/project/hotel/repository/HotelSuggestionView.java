package com.company.project.hotel.repository;

/** A listed hotel matching typed text, with the address parts its label is built from. */
public record HotelSuggestionView(String slug, String name, String district, String city) {
}
