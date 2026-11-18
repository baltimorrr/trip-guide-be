package com.company.project.payment.model;

import java.util.Set;

import com.company.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The only conversion between this project's minor units (ISO 4217 digits, see {@code Money}) and the amounts Stripe
 * expects. They are the same except for Stripe's special cases (https://docs.stripe.com/currencies): ISK and UGX
 * have no minor unit in ISO 4217 but Stripe still counts them in hundredths, and three-decimal currencies can only be
 * charged in tens of their smallest unit.
 */
public final class StripeAmounts {

	private static final Set<String> COUNTED_IN_HUNDREDTHS = Set.of("ISK", "UGX");

	private static final Set<String> THREE_DECIMALS = Set.of("BHD", "JOD", "KWD", "OMR", "TND");

	private StripeAmounts() {
	}

	/**
	 * Stripe's amount for {@code minor} units of {@code currency} (an upper-case ISO code). BusinessException 409
	 * for a three-decimal amount Stripe can't charge.
	 */
	public static long toStripe(long minor, String currency) {
		if (COUNTED_IN_HUNDREDTHS.contains(currency)) {
			return Math.multiplyExact(minor, 100L);
		}
		if (THREE_DECIMALS.contains(currency) && minor % 10 != 0) {
			throw new BusinessException(HttpStatus.CONFLICT,
					"Card payment in %s needs an amount in tens of its smallest unit".formatted(currency));
		}
		return minor;
	}

	/** This project's minor units for an amount Stripe reports in {@code currency} (an upper-case ISO code). */
	public static long fromStripe(long amount, String currency) {
		return COUNTED_IN_HUNDREDTHS.contains(currency) ? amount / 100 : amount;
	}

}
