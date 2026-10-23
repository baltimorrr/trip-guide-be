package com.company.project.search.dto;

import java.time.OffsetDateTime;

/**
 * Cancellation terms of a rate, for the card: "Free cancellation until 12 Oct" when {@code freeUntil} is set; the
 * policy name when the rate is refundable but free cancellation is over; "Non-refundable" otherwise.
 *
 * @param freeUntil last moment to cancel for free, with the hotel's UTC offset; null when there is none
 */
public record CancellationTerms(boolean refundable, OffsetDateTime freeUntil, String policyName) {
}
