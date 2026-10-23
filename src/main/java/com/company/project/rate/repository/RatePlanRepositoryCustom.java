package com.company.project.rate.repository;

import java.util.Collection;
import java.util.List;

/** Hand-written rate plan queries, implemented in {@code impl/RatePlanRepositoryImpl}. */
public interface RatePlanRepositoryCustom {

	/** Descriptions of these rate plans, in no particular order. */
	List<RateOfferView> findOfferDetails(Collection<Long> ratePlanIds);

}
