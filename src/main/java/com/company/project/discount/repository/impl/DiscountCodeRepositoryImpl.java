package com.company.project.discount.repository.impl;

import java.util.Optional;

import com.company.project.discount.model.DiscountCode;
import com.company.project.discount.repository.DiscountCodeRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

/**
 * Hand-written discount code queries, all JPQL. Spring Data finds this class by its name ({@code
 * DiscountCodeRepository} + "Impl") and merges it into the {@code DiscountCodeRepository} bean.
 */
@RequiredArgsConstructor
public class DiscountCodeRepositoryImpl implements DiscountCodeRepositoryCustom {

	private static final String LIVE_BY_CODE = """
			select c from DiscountCode c where c.code = :code and c.active and c.deletedAt is null""";

	private final EntityManager entityManager;

	@Override
	public Optional<DiscountCode> findLiveByCode(String code) {
		return liveByCode(code).getResultStream().findFirst();
	}

	@Override
	public Optional<DiscountCode> findLiveByCodeForUpdate(String code) {
		return liveByCode(code).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst();
	}

	private TypedQuery<DiscountCode> liveByCode(String code) {
		return entityManager.createQuery(LIVE_BY_CODE, DiscountCode.class).setParameter("code", code);
	}

}
