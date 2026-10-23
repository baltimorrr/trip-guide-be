package com.company.project.rate.repository;

import com.company.project.rate.model.RatePlan;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Rate plans. Spring Data generates CRUD; the hand-written queries are declared in {@link RatePlanRepositoryCustom}
 * and implemented in {@code impl/RatePlanRepositoryImpl}.
 */
public interface RatePlanRepository extends JpaRepository<RatePlan, Long>, RatePlanRepositoryCustom {

}
