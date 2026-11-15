package com.company.project.booking.dto;

import java.time.LocalTime;

/** @param checkInFrom the hotel's local time check-in opens, which an estimated arrival is compared with */
public record QuoteHotel(Long id, String slug, String name, String city, String countryCode, LocalTime checkInFrom) {
}
