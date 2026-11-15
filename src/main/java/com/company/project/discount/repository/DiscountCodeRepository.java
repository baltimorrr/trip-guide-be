package com.company.project.discount.repository;

import com.company.project.discount.model.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

/** Discount codes. Hand-written queries are in {@link DiscountCodeRepositoryCustom}. */
public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long>, DiscountCodeRepositoryCustom {

}
