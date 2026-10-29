package com.company.project.rate.repository;

import java.util.Collection;
import java.util.List;

/** Hand-written rate plan queries, implemented in {@code impl/RatePlanRepositoryImpl}. */
public interface RatePlanRepositoryCustom {

	/** Descriptions of these rate plans, in no particular order. */
	List<RateOfferView> findOfferDetails(Collection<Long> ratePlanIds);

	/** The rules of these rate plans' cancellation policies, per rate plan by hours before check-in. */
	List<CancellationRuleView> findCancellationRules(Collection<Long> ratePlanIds);

}
