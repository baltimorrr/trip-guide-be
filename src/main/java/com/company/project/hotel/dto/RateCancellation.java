package com.company.project.hotel.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * What cancelling one room of a rate costs, and when (C-HOTEL-06).
 *
 * @param freeUntil last moment to cancel for free, with the hotel's UTC offset; null when the rate is not refundable
 * or that moment has passed
 * @param penalties what cancelling costs after that, in time order; empty when cancelling is free until check-in
 */
public record RateCancellation(boolean refundable, String policyName, OffsetDateTime freeUntil,
		List<CancellationPenalty> penalties) {
}
