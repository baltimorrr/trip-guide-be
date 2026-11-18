package com.company.project.booking.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.company.project.booking.model.CancellationCharges.Room;
import com.company.project.rate.model.PenaltyType;
import org.junit.jupiter.api.Test;

class CancellationChargesTest {

	static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	static final LocalDate CHECK_IN = LocalDate.of(2026, 12, 20);

	static final LocalTime CHECK_IN_FROM = LocalTime.of(14, 0);

	static final ZonedDateTime CHECK_IN_AT = ZonedDateTime.of(CHECK_IN, CHECK_IN_FROM, ZONE);

	/** Free until 72 hours before check-in, then half the stay. */
	static final CancellationTerms HALF_WITHIN_72H = new CancellationTerms("Flexible", true,
			List.of(new CancellationTerms.Rule(72, PenaltyType.PERCENT, new BigDecimal("50"))));

	static final CancellationTerms NON_REFUNDABLE = new CancellationTerms("Non-refundable", false,
			List.of(new CancellationTerms.Rule(100_000, PenaltyType.PERCENT, new BigDecimal("100"))));

	/** Two nights of 1,000,000 VND. */
	static Room room(CancellationTerms terms) {
		return new Room(terms, List.of(1_000_000L, 1_000_000L), 2_000_000);
	}

	static Instant hoursBeforeCheckIn(int hours) {
		return CHECK_IN_AT.minusHours(hours).toInstant();
	}

	static CancellationCharges charges(List<Room> rooms, long subtotal, long paid, Instant now) {
		return CancellationCharges.of(rooms, CHECK_IN, CHECK_IN_FROM, ZONE, "VND", subtotal, paid, now);
	}

	@Test
	void whileCancellingIsFreeEverythingGoesBack() {
		assertThat(charges(List.of(room(HALF_WITHIN_72H)), 2_000_000, 2_200_000, hoursBeforeCheckIn(100)))
			.isEqualTo(new CancellationCharges(2_200_000, 0, 2_200_000));
	}

	@Test
	void aNonRefundableRoomGivesNothingBackEvenWithTaxOnTop() {
		// 2,000,000 of rooms plus 10% VAT on top.
		assertThat(charges(List.of(room(NON_REFUNDABLE)), 2_000_000, 2_200_000, hoursBeforeCheckIn(500)))
			.isEqualTo(new CancellationCharges(2_200_000, 2_000_000, 0));
	}

	@Test
	void aPenaltyKeepsTheSameShareOfTheDiscountedTotalWithItsTax() {
		// 2,000,000 − a 300,000 discount + 10% VAT on 1,700,000 = 1,870,000 paid; half the rooms are kept.
		assertThat(charges(List.of(room(HALF_WITHIN_72H)), 2_000_000, 1_870_000, hoursBeforeCheckIn(10)))
			.isEqualTo(new CancellationCharges(1_870_000, 1_000_000, 935_000));
	}

	@Test
	void theRefundIsRoundedHalfUpOnce() {
		// 1,000,001 paid × 1,000,000 / 2,000,000 = 500,000.5
		assertThat(charges(List.of(room(HALF_WITHIN_72H)), 2_000_000, 1_000_001, hoursBeforeCheckIn(10)).refundMinor())
			.isEqualTo(500_001);
	}

	@Test
	void eachRoomCostsItsOwnPolicysPenalty() {
		List<Room> rooms = List.of(room(HALF_WITHIN_72H), room(NON_REFUNDABLE));

		// Half of the first room and all of the second: 3,000,000 of 4,000,000 kept.
		assertThat(charges(rooms, 4_000_000, 4_000_000, hoursBeforeCheckIn(10)))
			.isEqualTo(new CancellationCharges(4_000_000, 3_000_000, 1_000_000));
		// Before 72 hours only the non-refundable room costs anything.
		assertThat(charges(rooms, 4_000_000, 4_000_000, hoursBeforeCheckIn(100)))
			.isEqualTo(new CancellationCharges(4_000_000, 2_000_000, 2_000_000));
	}

	@Test
	void thePenaltyStartsExactlyAtItsTier() {
		Instant start = hoursBeforeCheckIn(72);
		// At its start the tier applies (FreeCancellation.until ends there too); a second earlier it is still free.
		assertThat(charges(List.of(room(HALF_WITHIN_72H)), 2_000_000, 2_000_000, start).penaltyMinor())
			.isEqualTo(1_000_000);
		assertThat(charges(List.of(room(HALF_WITHIN_72H)), 2_000_000, 2_000_000, start.plusSeconds(1)).penaltyMinor())
			.isEqualTo(1_000_000);
		assertThat(charges(List.of(room(HALF_WITHIN_72H)), 2_000_000, 2_000_000, start.minusSeconds(1)).penaltyMinor())
			.isZero();
	}

	@Test
	void aPenaltyAboveTheSubtotalRefundsNothing() {
		CancellationTerms fixed = new CancellationTerms("Fixed fee", true,
				List.of(new CancellationTerms.Rule(24, PenaltyType.FIXED, new BigDecimal("1500000"))));
		List<Room> rooms = List.of(room(fixed), room(fixed));

		// 1,500,000 per room, 3,000,000 in all, against a subtotal cut to 2,000,000 by the caller's figures.
		assertThat(charges(rooms, 2_000_000, 1_800_000, hoursBeforeCheckIn(1)))
			.isEqualTo(new CancellationCharges(1_800_000, 2_000_000, 0));
	}

	@Test
	void aBookingWithNothingPaidRefundsNothing() {
		assertThat(charges(List.of(room(HALF_WITHIN_72H)), 2_000_000, 0, hoursBeforeCheckIn(100)))
			.isEqualTo(new CancellationCharges(0, 0, 0));
	}

}
