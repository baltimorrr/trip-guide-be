package com.company.project.discount.repository;

import java.util.Optional;

import com.company.project.discount.model.DiscountCode;

/** Hand-written discount code queries, implemented in {@code impl/DiscountCodeRepositoryImpl}. */
public interface DiscountCodeRepositoryCustom {

	/** The active, not deleted code stored as {@code code} (already {@linkplain DiscountCode#normalize normalized}). */
	Optional<DiscountCode> findLiveByCode(String code);

	/**
	 * Like {@link #findLiveByCode}, row-locked until the transaction ends, so two bookings using one code count its
	 * uses one after the other.
	 */
	Optional<DiscountCode> findLiveByCodeForUpdate(String code);

}
