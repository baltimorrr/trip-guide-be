package com.company.project.booking.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One cart group priced now, with what the guest form starts from. Send {@code price.total} back as the booking's
 * {@code expectedTotal}.
 *
 * @param booker pre-filled from the profile; the phone and country are null when the profile has none
 */
public record CheckoutQuote(QuoteHotel hotel, LocalDate checkIn, LocalDate checkOut, int nights, List<QuoteLine> lines,
		PriceBreakdown price, BookerDetails booker) {
}
