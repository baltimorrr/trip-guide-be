package com.company.project.rate.repository.impl;

import java.util.Collection;
import java.util.List;

import com.company.project.rate.repository.CancellationRuleView;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.rate.repository.RatePlanRepositoryCustom;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Hand-written rate plan queries, all JPQL. Spring Data finds this class by its name ({@code RatePlanRepository} +
 * "Impl") and merges it into the {@code RatePlanRepository} bean.
 */
@RequiredArgsConstructor
public class RatePlanRepositoryImpl implements RatePlanRepositoryCustom {

	// A rule with a zero penalty costs nothing, so only rules with a penalty end free cancellation.
	private static final String OFFER_DETAILS = """
			select new com.company.project.rate.repository.RateOfferView(
			       rp.id, rp.name, rt.name, b.code, b.name, cp.name, cp.refundable,
			       (select max(r.hoursBeforeCheckIn) from CancellationPolicyRule r
			        where r.policy = cp and r.penaltyValue > 0),
			       h.timezone, h.checkInFrom)
			from RatePlan rp
			join rp.roomType rt
			join rt.hotel h
			join rp.boardType b
			join rp.cancellationPolicy cp
			where rp.id in :ids""";

	private static final String CANCELLATION_RULES = """
			select new com.company.project.rate.repository.CancellationRuleView(
			       rp.id, r.hoursBeforeCheckIn, r.penaltyType, r.penaltyValue)
			from RatePlan rp
			join CancellationPolicyRule r on r.policy = rp.cancellationPolicy
			where rp.id in :ids
			order by rp.id, r.hoursBeforeCheckIn""";

	private final EntityManager entityManager;

	@Override
	public List<RateOfferView> findOfferDetails(Collection<Long> ratePlanIds) {
		return entityManager.createQuery(OFFER_DETAILS, RateOfferView.class)
			.setParameter("ids", ratePlanIds)
			.getResultList();
	}

	@Override
	public List<CancellationRuleView> findCancellationRules(Collection<Long> ratePlanIds) {
		return entityManager.createQuery(CANCELLATION_RULES, CancellationRuleView.class)
			.setParameter("ids", ratePlanIds)
			.getResultList();
	}

}
