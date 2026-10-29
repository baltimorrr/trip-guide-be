package com.company.project.hotel.dto;

import java.math.BigDecimal;

/**
 * Where the hotel is, as parts and as one line.
 *
 * @param countryName the country's display name; null until the destination sync has named it
 * @param formatted the parts that exist, street to country: "120 Võ Nguyên Giáp, Phước Mỹ, Sơn Trà, Đà Nẵng, Vietnam";
 * ends with the country code while the country has no name
 * @param latitude null with {@code longitude} when the location is not known
 */
public record HotelAddress(String line1, String ward, String district, String city, String countryCode,
		String countryName, String formatted, BigDecimal latitude, BigDecimal longitude) {
}
