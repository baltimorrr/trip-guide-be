package com.company.project.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.company.project.booking.model.BookingStatus;

/**
 * A booking. A new one is {@code PENDING}: its rooms are held until {@code holdExpiresAt}, when it expires unless paid.
 *
 * @param reference what the customer and the hotel quote, e.g. "HB7K2M4QXA"
 */
public record BookingResponse(String reference, BookingStatus status, Instant holdExpiresAt, QuoteHotel hotel,
		LocalDate checkIn, LocalDate checkOut, int nights, BookerDetails booker, String guestName,
		String specialRequests, LocalTime estimatedArrival, List<BookedRoom> rooms, PriceBreakdown price,
		Instant createdAt) {
}
