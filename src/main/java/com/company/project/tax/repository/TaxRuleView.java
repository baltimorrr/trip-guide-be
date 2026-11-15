package com.company.project.tax.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.company.project.tax.model.TaxCalcType;

/**
 * A tax rule as checkout prices it.
 *
 * @param value a percent for {@code PERCENT}, else major units of {@code currency}
 * @param validTo the last night it applies to (inclusive); null = no end, like a null {@code validFrom} = no start
 */
public record TaxRuleView(Long id, String name, TaxCalcType calcType, BigDecimal value, String currency,
		boolean included, boolean appliesAfterDiscount, LocalDate validFrom, LocalDate validTo) {
}
