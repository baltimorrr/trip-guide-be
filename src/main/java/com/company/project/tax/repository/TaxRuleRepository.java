package com.company.project.tax.repository;

import com.company.project.tax.model.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tax rules. Spring Data generates CRUD; the hand-written queries are declared in {@link TaxRuleRepositoryCustom}
 * and implemented in {@code impl/TaxRuleRepositoryImpl}.
 */
public interface TaxRuleRepository extends JpaRepository<TaxRule, Long>, TaxRuleRepositoryCustom {

}
