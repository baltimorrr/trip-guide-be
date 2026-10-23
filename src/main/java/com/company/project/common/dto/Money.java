package com.company.project.common.dto;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * An amount in major units of an ISO 4217 currency: {@code {"amount": 129.50, "currency": "USD"}},
 * {@code {"amount": 2400000, "currency": "VND"}}. The database keeps money as whole minor units ({@code *_minor}).
 */
public record Money(BigDecimal amount, String currency) {

	/** {@code minor} cents (or the currency's smallest unit), scaled by the currency's ISO 4217 decimal digits. */
	public static Money ofMinor(long minor, String currency) {
		int digits = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
		return new Money(BigDecimal.valueOf(minor, digits), currency);
	}

}
