package com.company.project.booking.dto;

import java.time.LocalTime;

import com.company.project.hotel.dto.HotelAddress;

/**
 * The booked hotel, with what a confirmation lists: its address and its contact for guests.
 *
 * @param checkInFrom when check-in opens, hotel's local time
 * @param checkOutUntil latest check-out, hotel's local time
 */
public record BookingHotel(Long id, String slug, String name, String city, String countryCode, HotelAddress address,
		String phone, String email, LocalTime checkInFrom, LocalTime checkOutUntil) {
}
