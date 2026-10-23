package com.company.project.rate.repository;

import java.time.LocalTime;

/**
 * How a rate plan is described on a search result: its names, board and cancellation terms, plus the hotel's time
 * zone and check-in time that the cancellation deadline is counted from.
 *
 * @param penaltyFromHours the largest {@code hours_before_check_in} of the policy's rules with a penalty, null when
 * no rule costs anything: cancelling is free until that many hours before check-in
 */
public record RateOfferView(Long ratePlanId, String ratePlanName, String roomTypeName, String boardCode,
		String boardName, String policyName, boolean refundable, Integer penaltyFromHours, String hotelTimezone,
		LocalTime checkInFrom) {
}
