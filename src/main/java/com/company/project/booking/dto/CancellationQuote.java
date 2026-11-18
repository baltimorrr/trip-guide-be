package com.company.project.booking.dto;

import java.time.Instant;

import com.company.project.common.dto.Money;

/**
 * What cancelling the booking now costs and gives back (C-MYBK-03), to show before the customer confirms. Send
 * {@code refund} back as the cancellation's {@code expectedRefund}: when a penalty tier starts in between, the
 * cancellation answers 409 with the new refund.
 *
 * @param paid what the customer paid
 * @param penalty what the rooms' cancellation policies keep
 * @param refund what goes back to the card; 0 for a non-refundable rate
 * @param asOf the moment these were computed for
 */
public record CancellationQuote(String reference, Money paid, Money penalty, Money refund, Instant asOf) {
}
