package com.company.project.booking.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import com.company.project.search.model.CancellationSchedule;

/**
 * What cancelling a whole booking costs at a moment, and what goes back to the card (C-MYBK-03, C-PAY-05). The only
 * place this is computed, so the preview and the cancellation always agree.
 * <p>
 * Each room costs the penalty of its policy's tier in effect ({@link CancellationSchedule}, counted from the hotel's
 * check-in time in its zone, as on the hotel page); a non-refundable room costs its whole stay. The refund is the
 * paid amount scaled by the share of the room subtotal that isn't kept:
 * {@code paid × (subtotal − penalty) ÷ subtotal}, rounded half up once (decided 15 Nov 2026), so the discount and
 * the taxes are given back in the same proportion. Free cancellation refunds everything, non-refundable nothing.
 *
 * @param paidMinor what the customer paid
 * @param penaltyMinor what the policies keep, at most the room subtotal
 * @param refundMinor what goes back, between 0 and {@code paidMinor}
 */
public record CancellationCharges(long paidMinor, long penaltyMinor, long refundMinor) {

	/**
	 * One booked room.
	 *
	 * @param nightPricesMinor its price per night, first night first
	 * @param totalMinor its price for the whole stay
	 */
	public record Room(CancellationTerms terms, List<Long> nightPricesMinor, long totalMinor) {
	}

	/**
	 * @param checkInFrom the hotel's check-in time, in {@code zone}
	 * @param subtotalMinor the booking's room subtotal, before the discount
	 */
	public static CancellationCharges of(List<Room> rooms, LocalDate checkIn, LocalTime checkInFrom, ZoneId zone,
			String currency, long subtotalMinor, long paidMinor, Instant now) {
		long penalty = 0;
		for (Room room : rooms) {
			penalty += penalty(room, checkIn, checkInFrom, zone, currency, now);
		}
		penalty = Math.min(penalty, subtotalMinor);
		return new CancellationCharges(paidMinor, penalty, refund(paidMinor, subtotalMinor, penalty));
	}

	private static long penalty(Room room, LocalDate checkIn, LocalTime checkInFrom, ZoneId zone, String currency,
			Instant now) {
		if (!room.terms().refundable()) {
			return room.totalMinor();
		}
		List<CancellationSchedule.Rule> rules = room.terms()
			.rules()
			.stream()
			.map(rule -> new CancellationSchedule.Rule(rule.hoursBeforeCheckIn(), rule.penaltyType(), rule.penaltyValue()))
			.toList();
		// The tier in effect now is the one without a start; none means cancelling is still free.
		return CancellationSchedule.tiers(checkIn, checkInFrom, zone, rules, room.nightPricesMinor(), currency, now)
			.stream()
			.filter(tier -> tier.from() == null)
			.findFirst()
			.map(CancellationSchedule.Tier::penaltyMinor)
			.orElse(0L);
	}

	private static long refund(long paidMinor, long subtotalMinor, long penaltyMinor) {
		if (paidMinor <= 0 || subtotalMinor <= 0) {
			return 0;
		}
		long refund = BigDecimal.valueOf(paidMinor)
			.multiply(BigDecimal.valueOf(subtotalMinor - penaltyMinor))
			.divide(BigDecimal.valueOf(subtotalMinor), 0, RoundingMode.HALF_UP)
			.longValueExact();
		return Math.clamp(refund, 0, paidMinor);
	}

}
