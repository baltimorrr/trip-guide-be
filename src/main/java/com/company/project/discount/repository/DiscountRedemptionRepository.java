package com.company.project.discount.repository;

import java.util.Collection;

import com.company.project.discount.model.DiscountRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

/** Uses of discount codes, counted by the usage limits. */
public interface DiscountRedemptionRepository extends JpaRepository<DiscountRedemption, Long> {

	long countByDiscountCodeId(Long discountCodeId);

	long countByDiscountCodeIdAndUserId(Long discountCodeId, Long userId);

	long deleteByBookingIdIn(Collection<Long> bookingIds);

}
