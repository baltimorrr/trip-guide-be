package com.company.project.booking.model;

import java.math.BigDecimal;
import java.util.List;

import com.company.project.rate.model.PenaltyType;

/**
 * A rate plan's cancellation policy as it was when the room was booked ({@code booking_rooms.cancellation_policy},
 * jsonb), so a later change to the policy doesn't change what the customer agreed to.
 */
public record CancellationTerms(String name, boolean refundable, List<Rule> rules) {

	/** Cancelling less than {@code hoursBeforeCheckIn} hours before check-in costs the penalty. */
	public record Rule(int hoursBeforeCheckIn, PenaltyType penaltyType, BigDecimal penaltyValue) {
	}

}
