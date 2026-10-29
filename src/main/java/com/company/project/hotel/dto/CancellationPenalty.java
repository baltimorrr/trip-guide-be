package com.company.project.hotel.dto;

import java.time.OffsetDateTime;

import com.company.project.common.dto.Money;

/**
 * Cancelling one room between {@code from} and {@code until} costs {@code amount}.
 *
 * @param from with the hotel's UTC offset; null when it already applies
 * @param until null for the last penalty, which also applies after check-in and to a no-show
 */
public record CancellationPenalty(OffsetDateTime from, OffsetDateTime until, Money amount) {
}
