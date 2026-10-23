package com.company.project.common.validation;

import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class IsoCurrencyValidator implements ConstraintValidator<IsoCurrency, String> {

	private static final Set<String> CURRENCIES = Currency.getAvailableCurrencies().stream()
		.map(Currency::getCurrencyCode)
		.collect(Collectors.toUnmodifiableSet());

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || CURRENCIES.contains(value.trim().toUpperCase(Locale.ROOT));
	}

}
