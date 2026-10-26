package com.company.project.search.model;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.company.project.support.CatalogFixtures;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The SQL function {@code free_cancellation_until()} (V6), which the search filters with, must give the same deadline
 * as {@link FreeCancellation#until}, which the result cards show.
 */
@IntegrationTest
class FreeCancellationSqlTest {

	static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");

	static final LocalDate CHECK_IN = LocalDate.of(2026, 11, 20);

	static final LocalTime TWO_PM = LocalTime.of(14, 0);

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
	}

	@Test
	void nonRefundablePolicyHasNoDeadline() {
		long policy = catalog.cancellationPolicy("Non-refundable " + uniqueSuffix(), false);
		catalog.cancellationRule(policy, 100_000, "PERCENT", "100");

		assertThat(sqlDeadline(policy, CHECK_IN, TWO_PM, SAIGON)).isNull();
		assertThat(javaDeadline(false, null, CHECK_IN, TWO_PM, SAIGON)).isNull();
	}

	@Test
	void withoutRulesCancellingIsFreeUntilCheckIn() {
		long policy = catalog.cancellationPolicy("Free " + uniqueSuffix(), true);

		assertSameDeadline(policy, null, CHECK_IN, TWO_PM, SAIGON);
		assertThat(sqlDeadline(policy, CHECK_IN, TWO_PM, SAIGON)).isEqualTo(Instant.parse("2026-11-20T07:00:00Z"));
	}

	@Test
	void theLargestPenalisedHoursEndTheFreePeriod() {
		long policy = catalog.cancellationPolicy("48h " + uniqueSuffix(), true);
		catalog.cancellationRule(policy, 24, "PERCENT", "100");
		catalog.cancellationRule(policy, 48, "NIGHTS", "1");
		catalog.cancellationRule(policy, 72, "PERCENT", "0"); // costs nothing, so it does not end the free period

		assertSameDeadline(policy, 48, CHECK_IN, TWO_PM, SAIGON);
		assertThat(sqlDeadline(policy, CHECK_IN, TWO_PM, SAIGON)).isEqualTo(Instant.parse("2026-11-18T07:00:00Z"));
	}

	@Test
	void hoursAreCountedAcrossADaylightSavingChange() {
		long policy = catalog.cancellationPolicy("24h " + uniqueSuffix(), true);
		catalog.cancellationRule(policy, 24, "PERCENT", "100");
		LocalDate paris = LocalDate.of(2026, 10, 25); // Paris leaves summer time at 03:00 that day

		assertSameDeadline(policy, 24, paris, TWO_PM, ZoneId.of("Europe/Paris"));
		assertThat(sqlDeadline(policy, paris, TWO_PM, ZoneId.of("Europe/Paris")))
			.isEqualTo(OffsetDateTime.parse("2026-10-24T15:00:00+02:00").toInstant());
	}

	private void assertSameDeadline(long policy, Integer penaltyFromHours, LocalDate checkIn, LocalTime checkInFrom,
			ZoneId zone) {
		assertThat(sqlDeadline(policy, checkIn, checkInFrom, zone))
			.isEqualTo(javaDeadline(true, penaltyFromHours, checkIn, checkInFrom, zone));
	}

	private Instant sqlDeadline(long policy, LocalDate checkIn, LocalTime checkInFrom, ZoneId zone) {
		OffsetDateTime deadline = jdbc.sql("select free_cancellation_until(:policy, :checkIn, :checkInFrom, :zone)")
			.param("policy", policy)
			.param("checkIn", checkIn)
			.param("checkInFrom", checkInFrom)
			.param("zone", zone.getId())
			.query(OffsetDateTime.class)
			.list()
			.getFirst();
		return deadline == null ? null : deadline.toInstant();
	}

	// Instant.EPOCH as "now" keeps every deadline in the future, so the Java side never drops it.
	private static Instant javaDeadline(boolean refundable, Integer penaltyFromHours, LocalDate checkIn,
			LocalTime checkInFrom, ZoneId zone) {
		OffsetDateTime deadline = FreeCancellation.until(checkIn, checkInFrom, zone, refundable, penaltyFromHours,
				Instant.EPOCH);
		return deadline == null ? null : deadline.toInstant();
	}

}
