package com.company.project.booking.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.company.project.booking.model.BookingStatus;

/**
 * A booking, as its confirmation shows it (C-PAY-03). A new one is {@code PENDING}: its rooms are held until
 * {@code holdExpiresAt}, when it expires unless paid; the card payment confirms it.
 *
 * @param reference what the customer and the hotel quote, e.g. "HB7K2M4QXA"
 * @param holdExpiresAt until when a Pending booking holds its rooms
 * @param confirmedAt when the payment confirmed it; null before
 * @param rooms each room booked, with the cancellation terms it was sold with, as they stand now
 * @param payment the latest card payment; null before payment starts or when there was nothing to pay
 * @param cancellation null unless cancelled
 */
public record BookingResponse(String reference, BookingStatus status, Instant holdExpiresAt, Instant confirmedAt,
		BookingHotel hotel, LocalDate checkIn, LocalDate checkOut, int nights, BookerDetails booker, String guestName,
		String specialRequests, LocalTime estimatedArrival, List<BookedRoom> rooms, PriceBreakdown price,
		PaymentDetails payment, BookingCancellation cancellation, Instant createdAt) {
}
