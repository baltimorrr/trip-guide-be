package com.company.project.tax.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/** Hand-written tax rule queries, implemented in {@code impl/TaxRuleRepositoryImpl}. */
public interface TaxRuleRepositoryCustom {

	/**
	 * The countries among {@code countryCodes} with an active tax that room prices do not include and that applies
	 * to at least one night of {@code [firstNight, lastNight]}.
	 */
	Set<String> findCountriesWithTaxesOnTop(Collection<String> countryCodes, LocalDate firstNight,
			LocalDate lastNight);

}
