package com.company.project.tax.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import com.company.project.common.dto.Money;
import com.company.project.common.exception.BusinessException;
import com.company.project.tax.repository.TaxRuleView;
import org.springframework.http.HttpStatus;

/**
 * The only place tax amounts are computed (C-BOOK-02). A rule taxes only the nights inside its validity, each rule's
 * amount is rounded half up to whole minor units once, and included taxes are worked out from the price they are
 * already in.
 * <ul>
 * <li>{@code PERCENT} on top: value % of those nights' room price; included: {@code price × v / (100 + v)}. With
 * {@code applies_after_discount} the price is first reduced in proportion to the discount.</li>
 * <li>{@code PER_NIGHT}: value × rooms × nights; {@code PER_PERSON_NIGHT}: value × guests × nights;
 * {@code PER_BOOKING}: value once.</li>
 * </ul>
 */
public final class TaxCalculator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private TaxCalculator() {
	}

	/** One night of the stay and what all the booked rooms cost for it, in minor units. */
	public record Night(LocalDate date, long roomsPriceMinor) {
	}

	/**
	 * What the taxes are worked out from: the booking's currency, its nights, how many rooms and guests (adults and
	 * children of every room) it has, and the discount off the room subtotal (the sum of the nights).
	 */
	public record Stay(String currency, List<Night> nights, int rooms, int guests, long discountMinor) {

		long subtotalMinor() {
			return nights.stream().mapToLong(Night::roomsPriceMinor).sum();
		}

	}

	/**
	 * One line per rule that covers a night of the stay, in the rules' order. A fixed-amount rule in another currency
	 * can't be converted yet, so it refuses the booking (409) rather than charge too little tax.
	 */
	public static List<TaxLine> taxes(Stay stay, List<TaxRuleView> rules) {
		return rules.stream()
			.map(rule -> new Covered(rule, stay.nights().stream().filter(night -> covers(rule, night.date())).toList()))
			.filter(covered -> !covered.nights().isEmpty())
			.map(covered -> new TaxLine(covered.rule().id(), covered.rule().name(), covered.rule().included(),
					amount(covered.rule(), covered.nights(), stay)))
			.toList();
	}

	private static long amount(TaxRuleView rule, List<Night> nights, Stay stay) {
		if (rule.calcType() == TaxCalcType.PERCENT) {
			return percent(rule, nights, stay);
		}
		if (!stay.currency().equals(rule.currency())) {
			throw new BusinessException(HttpStatus.CONFLICT, "Taxes for this hotel can't be calculated yet");
		}
		int count = switch (rule.calcType()) {
			case PER_NIGHT -> stay.rooms() * nights.size();
			case PER_PERSON_NIGHT -> stay.guests() * nights.size();
			case PER_BOOKING -> 1;
			case PERCENT -> throw new IllegalStateException();
		};
		return Money.toMinor(rule.value().multiply(BigDecimal.valueOf(count)), stay.currency());
	}

	// price × factor × v / 100 (on top) or / (100 + v) (included), where factor = (subtotal - discount) / subtotal
	// after a discount; one division, so the result is rounded once.
	private static long percent(TaxRuleView rule, List<Night> nights, Stay stay) {
		BigDecimal price = BigDecimal.valueOf(nights.stream().mapToLong(Night::roomsPriceMinor).sum());
		BigDecimal numerator = price.multiply(rule.value());
		BigDecimal denominator = rule.included() ? HUNDRED.add(rule.value()) : HUNDRED;
		long subtotal = stay.subtotalMinor();
		if (rule.appliesAfterDiscount() && stay.discountMinor() > 0 && subtotal > 0) {
			numerator = numerator.multiply(BigDecimal.valueOf(subtotal - stay.discountMinor()));
			denominator = denominator.multiply(BigDecimal.valueOf(subtotal));
		}
		return numerator.divide(denominator, 0, RoundingMode.HALF_UP).longValueExact();
	}

	private static boolean covers(TaxRuleView rule, LocalDate night) {
		return (rule.validFrom() == null || !night.isBefore(rule.validFrom()))
				&& (rule.validTo() == null || !night.isAfter(rule.validTo()));
	}

	private record Covered(TaxRuleView rule, List<Night> nights) {
	}

}
