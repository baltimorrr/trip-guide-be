package com.company.project.search.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Until when a booking can be cancelled for free. A policy rule means "cancelling less than N hours before check-in
 * costs the penalty", and the smallest N still greater than the hours left applies; so cancelling stays free until
 * the largest N with a penalty starts to apply. Check-in is the hotel's check-in time in its own time zone.
 */
public final class FreeCancellation {

	private FreeCancellation() {
	}

	/**
	 * @param penaltyFromHours the largest N whose rule has a penalty; null when no rule costs anything (free until
	 * check-in)
	 * @return the deadline with the hotel's UTC offset, or null when the policy is not refundable or the deadline is
	 * not after {@code now}
	 */
	public static OffsetDateTime until(LocalDate checkIn, LocalTime checkInFrom, ZoneId zone, boolean refundable,
			Integer penaltyFromHours, Instant now) {
		if (!refundable) {
			return null;
		}
		ZonedDateTime checkInAt = ZonedDateTime.of(checkIn, checkInFrom, zone);
		// minusHours works on the instant timeline, so a daylight-saving change in between is counted right.
		ZonedDateTime deadline = penaltyFromHours == null ? checkInAt : checkInAt.minusHours(penaltyFromHours);
		return deadline.toInstant().isAfter(now) ? deadline.toOffsetDateTime() : null;
	}

}
