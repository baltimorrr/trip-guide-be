package com.company.project.common.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * An amount in major units of an ISO 4217 currency: {@code {"amount": 129.50, "currency": "USD"}},
 * {@code {"amount": 2400000, "currency": "VND"}}. The database keeps money as whole minor units ({@code *_minor}).
 */
public record Money(BigDecimal amount, String currency) {

	/** {@code minor} cents (or the currency's smallest unit), scaled by the currency's ISO 4217 decimal digits. */
	public static Money ofMinor(long minor, String currency) {
		return new Money(BigDecimal.valueOf(minor, digits(currency)), currency);
	}

	/**
	 * Whole minor units of {@code amount} major units, rounding half up a fraction smaller than the currency's
	 * smallest unit. Throws IllegalArgumentException for a code that is not ISO 4217.
	 */
	public static long toMinor(BigDecimal amount, String currency) {
		return amount.movePointRight(digits(currency)).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	private static int digits(String currency) {
		return Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
	}

}
