package com.company.project.rate.repository;

import java.math.BigDecimal;

import com.company.project.rate.model.PenaltyType;

/** A rule of a rate plan's cancellation policy: cancelling less than that many hours before check-in costs it. */
public record CancellationRuleView(Long ratePlanId, int hoursBeforeCheckIn, PenaltyType penaltyType,
		BigDecimal penaltyValue) {
}
