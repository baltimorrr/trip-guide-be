package com.company.project.booking.model;

/**
 * A Pending booking was just confirmed (paid, or nothing to pay). Published inside the confirming transaction;
 * listeners that send emails run after it commits.
 */
public record BookingConfirmed(long bookingId) {
}
