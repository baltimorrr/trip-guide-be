package com.company.project.rate.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Hand-written rate plan queries, implemented in {@code impl/RatePlanRepositoryImpl}. */
public interface RatePlanRepositoryCustom {

	/** Descriptions of these rate plans, in no particular order. */
	List<RateOfferView> findOfferDetails(Collection<Long> ratePlanIds);

	/** The rules of these rate plans' cancellation policies, per rate plan by hours before check-in. */
	List<CancellationRuleView> findCancellationRules(Collection<Long> ratePlanIds);

	/**
	 * The rate plan's room type and hotel, whatever their status; empty when there is no such plan. Whether it can
	 * be sold is the room list query's call.
	 */
	Optional<RatePlanSellerView> findSeller(long ratePlanId);

}
