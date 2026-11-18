package com.company.project.booking.dto;

/**
 * What a booking's emails need: the booking as its page shows it, and where the hotel is told.
 *
 * @param ownerEmail the hotel partner's contact email; null when there is none
 */
public record BookingNotice(BookingResponse booking, String ownerEmail) {
}
