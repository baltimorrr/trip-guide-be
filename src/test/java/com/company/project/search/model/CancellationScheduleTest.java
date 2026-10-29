package com.company.project.search.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.company.project.rate.model.PenaltyType;
import com.company.project.search.model.CancellationSchedule.Rule;
import com.company.project.search.model.CancellationSchedule.Tier;
import org.junit.jupiter.api.Test;

class CancellationScheduleTest {

	private static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 10);

	private static final LocalTime CHECK_IN_FROM = LocalTime.of(14, 0);

	private static final Instant EARLY = Instant.parse("2026-11-01T00:00:00Z");

	private static final List<Long> TWO_NIGHTS = List.of(1_000_000L, 2_000_000L);

	@Test
	void eachRuleIsATierFromItsHoursUntilTheNextSmallerRule() {
		List<Tier> tiers = tiers(List.of(rule(24, PenaltyType.PERCENT, "100"), rule(72, PenaltyType.PERCENT, "50")),
				TWO_NIGHTS, EARLY);

		assertThat(tiers).containsExactly(
				new Tier(at("2026-11-07T14:00+07:00"), at("2026-11-09T14:00+07:00"), 1_500_000),
				new Tier(at("2026-11-09T14:00+07:00"), null, 3_000_000));
	}

	@Test
	void theFirstTierStartsWhenFreeCancellationEnds() {
		List<List<Rule>> policies = List.of(
				List.of(rule(24, PenaltyType.PERCENT, "100")),
				List.of(rule(72, PenaltyType.PERCENT, "50"), rule(24, PenaltyType.PERCENT, "100")),
				// A free rule before the paid one: cancelling stays free until the paid rule starts.
				List.of(rule(72, PenaltyType.PERCENT, "0"), rule(24, PenaltyType.NIGHTS, "1")));
		for (List<Rule> rules : policies) {
			Integer penaltyFromHours = rules.stream()
				.filter(r -> r.value().signum() > 0)
				.map(Rule::hoursBeforeCheckIn)
				.max(Integer::compare)
				.orElse(null);
			OffsetDateTime freeUntil = FreeCancellation.until(CHECK_IN, CHECK_IN_FROM, SAIGON, true, penaltyFromHours,
					EARLY);

			assertThat(tiers(rules, TWO_NIGHTS, EARLY).getFirst().from()).as(rules.toString()).isEqualTo(freeUntil);
		}
	}

	@Test
	void aPolicyWithoutPaidRulesHasNoTiers() {
		assertThat(tiers(List.of(rule(48, PenaltyType.PERCENT, "0")), TWO_NIGHTS, EARLY)).isEmpty();
		assertThat(tiers(List.of(), TWO_NIGHTS, EARLY)).isEmpty();
	}

	@Test
	void aNonRefundableRateCostsTheWholeStayFromNowOn() {
		assertThat(tiers(List.of(rule(100_000, PenaltyType.PERCENT, "100")), TWO_NIGHTS, EARLY))
			.containsExactly(new Tier(null, null, 3_000_000));
	}

	@Test
	void tiersThatEndedAreDroppedAndTheCurrentOneHasNoStart() {
		List<Rule> rules = List.of(rule(72, PenaltyType.PERCENT, "50"), rule(24, PenaltyType.PERCENT, "100"));

		assertThat(tiers(rules, TWO_NIGHTS, Instant.parse("2026-11-08T00:00:00Z"))).containsExactly(
				new Tier(null, at("2026-11-09T14:00+07:00"), 1_500_000),
				new Tier(at("2026-11-09T14:00+07:00"), null, 3_000_000));
		assertThat(tiers(rules, TWO_NIGHTS, Instant.parse("2026-11-10T00:00:00Z")))
			.containsExactly(new Tier(null, null, 3_000_000));
	}

	@Test
	void nightsPenaltiesChargeTheFirstNightsAndAFractionOfTheNext() {
		assertThat(penalty(rule(24, PenaltyType.NIGHTS, "1"), TWO_NIGHTS)).isEqualTo(1_000_000);
		assertThat(penalty(rule(24, PenaltyType.NIGHTS, "1.5"), TWO_NIGHTS)).isEqualTo(2_000_000);
		assertThat(penalty(rule(24, PenaltyType.NIGHTS, "0.25"), TWO_NIGHTS)).isEqualTo(250_000);
		assertThat(penalty(rule(24, PenaltyType.NIGHTS, "5"), TWO_NIGHTS)).isEqualTo(3_000_000);
		assertThat(penalty(rule(24, PenaltyType.NIGHTS, "9999999999.99"), TWO_NIGHTS)).isEqualTo(3_000_000);
	}

	@Test
	void fixedPenaltiesAreInTheRatesCurrencyUpToTheStay() {
		assertThat(penalty(rule(24, PenaltyType.FIXED, "300000.00"), TWO_NIGHTS)).isEqualTo(300_000);
		assertThat(penalty(rule(24, PenaltyType.FIXED, "9999999"), TWO_NIGHTS)).isEqualTo(3_000_000);
		List<Tier> usd = CancellationSchedule.tiers(CHECK_IN, CHECK_IN_FROM, SAIGON,
				List.of(rule(24, PenaltyType.FIXED, "25.50")), List.of(10_000L, 10_000L), "USD", EARLY);
		assertThat(usd.getFirst().penaltyMinor()).isEqualTo(2_550);
	}

	@Test
	void percentPenaltiesRoundHalfUpAndNeverExceedTheStay() {
		assertThat(penalty(rule(24, PenaltyType.PERCENT, "33.33"), List.of(1_005L))).isEqualTo(335);
		assertThat(penalty(rule(24, PenaltyType.PERCENT, "250"), TWO_NIGHTS)).isEqualTo(3_000_000);
	}

	@Test
	void aFreeRuleBoundsItsNeighboursButIsNotListed() {
		List<Rule> rules = List.of(rule(72, PenaltyType.PERCENT, "50"), rule(48, PenaltyType.PERCENT, "0"),
				rule(24, PenaltyType.PERCENT, "100"));

		assertThat(tiers(rules, TWO_NIGHTS, EARLY)).containsExactly(
				new Tier(at("2026-11-07T14:00+07:00"), at("2026-11-08T14:00+07:00"), 1_500_000),
				new Tier(at("2026-11-09T14:00+07:00"), null, 3_000_000));
	}

	@Test
	void hoursAreCountedOnTheInstantTimelineAcrossADaylightSavingChange() {
		// Berlin leaves summer time on 25 Oct 2026: 48 hours before 26 Oct 15:00 (+01:00) is 24 Oct 16:00 (+02:00).
		List<Tier> tiers = CancellationSchedule.tiers(LocalDate.of(2026, 10, 26), LocalTime.of(15, 0),
				ZoneId.of("Europe/Berlin"), List.of(rule(48, PenaltyType.PERCENT, "100")), TWO_NIGHTS, "EUR",
				Instant.parse("2026-10-01T00:00:00Z"));

		assertThat(tiers.getFirst().from()).isEqualTo(at("2026-10-24T16:00+02:00"));
	}

	private static List<Tier> tiers(List<Rule> rules, List<Long> nights, Instant now) {
		return CancellationSchedule.tiers(CHECK_IN, CHECK_IN_FROM, SAIGON, rules, nights, "VND", now);
	}

	private static long penalty(Rule rule, List<Long> nights) {
		return tiers(List.of(rule), nights, EARLY).getFirst().penaltyMinor();
	}

	private static Rule rule(int hours, PenaltyType type, String value) {
		return new Rule(hours, type, new BigDecimal(value));
	}

	private static OffsetDateTime at(String text) {
		return OffsetDateTime.parse(text);
	}

}
