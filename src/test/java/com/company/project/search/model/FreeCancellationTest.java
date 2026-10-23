package com.company.project.search.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class FreeCancellationTest {

	static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");

	static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 20);

	static final LocalTime TWO_PM = LocalTime.of(14, 0);

	static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	@Test
	void nonRefundableRateHasNoFreeCancellation() {
		assertThat(FreeCancellation.until(CHECK_IN, TWO_PM, SAIGON, false, null, NOW)).isNull();
	}

	@Test
	void withoutAPenaltyRuleCancellingIsFreeUntilCheckIn() {
		assertThat(FreeCancellation.until(CHECK_IN, TWO_PM, SAIGON, true, null, NOW))
			.isEqualTo(OffsetDateTime.parse("2026-11-20T14:00:00+07:00"));
	}

	@Test
	void freeUntilThePenaltyStartsInTheHotelsTimeZone() {
		assertThat(FreeCancellation.until(CHECK_IN, TWO_PM, SAIGON, true, 24, NOW))
			.isEqualTo(OffsetDateTime.parse("2026-11-19T14:00:00+07:00"));
	}

	@Test
	void deadlineThatHasPassedMeansNoFreeCancellation() {
		Instant afterDeadline = Instant.parse("2026-11-19T07:00:00Z"); // 14:00 in Saigon, the deadline itself
		assertThat(FreeCancellation.until(CHECK_IN, TWO_PM, SAIGON, true, 24, afterDeadline)).isNull();
		assertThat(FreeCancellation.until(CHECK_IN, TWO_PM, SAIGON, true, 24, afterDeadline.minusSeconds(1)))
			.isNotNull();
	}

	@Test
	void hoursAreCountedAcrossADaylightSavingChange() {
		// Paris leaves summer time at 03:00 on 25 Oct 2026, so 24 hours before 14:00 on the 25th is 15:00 on the 24th.
		assertThat(FreeCancellation.until(LocalDate.of(2026, 10, 25), TWO_PM, ZoneId.of("Europe/Paris"), true, 24,
				Instant.parse("2026-10-01T00:00:00Z")))
			.isEqualTo(OffsetDateTime.parse("2026-10-24T15:00:00+02:00"));
	}

}
