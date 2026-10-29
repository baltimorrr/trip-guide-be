package com.company.project.search.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.company.project.common.dto.Money;
import com.company.project.rate.model.PenaltyType;

/**
 * What cancelling one room of a rate costs, and from when (C-HOTEL-06). A policy rule means "cancelling less than N
 * hours before check-in costs the penalty", and the rule with the smallest N still greater than the hours left
 * applies. With the rules' hours h1 < ... < hk, rule i therefore applies after check-in − hi and up to check-in −
 * h(i−1), and rule 1 from its start on, including cancelling after check-in and a no-show. Check-in is the hotel's
 * check-in time in its own zone, as in {@link FreeCancellation}: the first tier starts where its free period ends.
 */
public final class CancellationSchedule {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private CancellationSchedule() {
	}

	/** A policy rule: cancelling less than {@code hoursBeforeCheckIn} hours before check-in costs the penalty. */
	public record Rule(int hoursBeforeCheckIn, PenaltyType type, BigDecimal value) {
	}

	/**
	 * A period during which cancelling costs {@code penaltyMinor}.
	 *
	 * @param from when it starts, with the hotel's UTC offset; null when it is already in effect
	 * @param until when it ends; null for the last tier, which lasts past check-in and covers a no-show
	 * @param penaltyMinor one room, in minor units of the rate's currency, at most the stay's price
	 */
	public record Tier(OffsetDateTime from, OffsetDateTime until, long penaltyMinor) {
	}

	/**
	 * The tiers still to come or in effect at {@code now}, in time order. Rules without a penalty are not listed,
	 * though they still end the tier before them.
	 *
	 * @param nightPricesMinor one room's price per night of the stay, first night first, in {@code currency}
	 */
	public static List<Tier> tiers(LocalDate checkIn, LocalTime checkInFrom, ZoneId zone, List<Rule> rules,
			List<Long> nightPricesMinor, String currency, Instant now) {
		ZonedDateTime checkInAt = ZonedDateTime.of(checkIn, checkInFrom, zone);
		List<Rule> byHours = rules.stream().sorted(Comparator.comparingInt(Rule::hoursBeforeCheckIn)).toList();
		long stay = nightPricesMinor.stream().mapToLong(Long::longValue).sum();
		List<Tier> tiers = new ArrayList<>();
		// The rule with the most hours starts first.
		for (int i = byHours.size() - 1; i >= 0; i--) {
			Rule rule = byHours.get(i);
			if (rule.value().signum() <= 0) {
				continue;
			}
			// minusHours works on the instant timeline, so a daylight-saving change in between is counted right.
			ZonedDateTime start = checkInAt.minusHours(rule.hoursBeforeCheckIn());
			ZonedDateTime end = i == 0 ? null : checkInAt.minusHours(byHours.get(i - 1).hoursBeforeCheckIn());
			if (end != null && !end.toInstant().isAfter(now)) {
				continue;
			}
			tiers.add(new Tier(start.toInstant().isAfter(now) ? start.toOffsetDateTime() : null,
					end == null ? null : end.toOffsetDateTime(), penalty(rule, stay, nightPricesMinor, currency)));
		}
		return List.copyOf(tiers);
	}

	private static long penalty(Rule rule, long stay, List<Long> nightPricesMinor, String currency) {
		long amount = switch (rule.type()) {
			case PERCENT -> percentOf(stay, rule.value().min(HUNDRED));
			case NIGHTS -> firstNights(rule.value(), nightPricesMinor);
			case FIXED -> Money.toMinor(rule.value(), currency);
		};
		return Math.min(amount, stay);
	}

	// The first whole nights, and the fraction of the next one pro rata.
	private static long firstNights(BigDecimal count, List<Long> nightPricesMinor) {
		BigDecimal capped = count.min(BigDecimal.valueOf(nightPricesMinor.size()));
		int whole = capped.intValue();
		long amount = nightPricesMinor.stream().limit(whole).mapToLong(Long::longValue).sum();
		BigDecimal fraction = capped.subtract(BigDecimal.valueOf(whole));
		return fraction.signum() > 0 ? amount + percentOf(nightPricesMinor.get(whole), fraction.multiply(HUNDRED))
				: amount;
	}

	// Half up to whole minor units.
	private static long percentOf(long minor, BigDecimal percent) {
		return BigDecimal.valueOf(minor).multiply(percent).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValueExact();
	}

}
