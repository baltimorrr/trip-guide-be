package com.company.project.common.validation;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class IsoCountryValidator implements ConstraintValidator<IsoCountry, String> {

	private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || COUNTRIES.contains(value.trim().toUpperCase(Locale.ROOT));
	}

}
