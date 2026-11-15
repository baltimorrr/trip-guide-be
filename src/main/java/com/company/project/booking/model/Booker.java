package com.company.project.booking.model;

/**
 * Who makes the booking and is contacted about it.
 *
 * @param countryCode ISO 3166 alpha-2
 */
public record Booker(String fullName, String email, String phone, String countryCode) {
}
