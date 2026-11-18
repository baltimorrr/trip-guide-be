package com.company.project.hotel.repository;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * A hotel as a booking shows it: name, address, contact and stay times. Read for any hotel, listed or not, since a
 * booking outlives its hotel's listing.
 *
 * @param countryName the COUNTRY destination's name; null when the destination sync has not named it yet
 * @param timezone IANA zone of the hotel, in which check-in times and cancellation deadlines are counted
 */
public record HotelContactView(Long id, String slug, String name, String line1, String ward, String district,
		String city, String countryCode, String countryName, BigDecimal latitude, BigDecimal longitude, String phone,
		String email, String timezone, LocalTime checkInFrom, LocalTime checkOutUntil) {
}
