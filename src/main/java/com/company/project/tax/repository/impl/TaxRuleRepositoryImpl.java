package com.company.project.tax.repository.impl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

import com.company.project.tax.repository.TaxRuleRepositoryCustom;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Hand-written tax rule queries, all JPQL. Spring Data finds this class by its name ({@code TaxRuleRepository} +
 * "Impl") and merges it into the {@code TaxRuleRepository} bean.
 */
@RequiredArgsConstructor
public class TaxRuleRepositoryImpl implements TaxRuleRepositoryCustom {

	private static final String COUNTRIES_WITH_TAXES_ON_TOP = """
			select distinct t.countryCode from TaxRule t
			where t.countryCode in :countries and t.active and t.included = false
			  and (t.validFrom is null or t.validFrom <= :lastNight)
			  and (t.validTo is null or t.validTo >= :firstNight)""";

	private final EntityManager entityManager;

	@Override
	public Set<String> findCountriesWithTaxesOnTop(Collection<String> countryCodes, LocalDate firstNight,
			LocalDate lastNight) {
		return Set.copyOf(entityManager.createQuery(COUNTRIES_WITH_TAXES_ON_TOP, String.class)
			.setParameter("countries", countryCodes)
			.setParameter("firstNight", firstNight)
			.setParameter("lastNight", lastNight)
			.getResultList());
	}

}
