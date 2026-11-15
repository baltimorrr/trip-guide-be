package com.company.project.tax.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.company.project.common.exception.BusinessException;
import com.company.project.tax.model.TaxCalculator.Night;
import com.company.project.tax.model.TaxCalculator.Stay;
import com.company.project.tax.repository.TaxRuleView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TaxCalculatorTest {

	static final LocalDate FIRST = LocalDate.of(2026, 12, 10);

	/** Three nights of 2 rooms at 1,000,000 VND each: a subtotal of 6,000,000. */
	static final List<Night> NIGHTS = List.of(new Night(FIRST, 2_000_000), new Night(FIRST.plusDays(1), 2_000_000),
			new Night(FIRST.plusDays(2), 2_000_000));

	@Test
	void aPercentOnTopIsChargedOnThePriceAfterTheDiscount() {
		TaxRuleView vat = percent("VAT", "10", false, true);

		assertThat(TaxCalculator.taxes(stay(0), List.of(vat)))
			.containsExactly(new TaxLine(1L, "VAT", false, 600_000));
		assertThat(TaxCalculator.taxes(stay(1_000_000), List.of(vat)))
			.containsExactly(new TaxLine(1L, "VAT", false, 500_000));
	}

	@Test
	void aPercentBeforeTheDiscountIgnoresIt() {
		assertThat(TaxCalculator.taxes(stay(1_000_000), List.of(percent("VAT", "10", false, false))))
			.containsExactly(new TaxLine(1L, "VAT", false, 600_000));
	}

	@Test
	void anIncludedPercentIsThePartAlreadyInsideThePrice() {
		// 8% inside 6,000,000: 6,000,000 × 8 / 108 = 444,444.4…
		assertThat(TaxCalculator.taxes(stay(0), List.of(percent("VAT", "8", true, true))))
			.containsExactly(new TaxLine(1L, "VAT", true, 444_444));
	}

	@Test
	void fixedTaxesCountRoomsGuestsNightsOrTheBooking() {
		List<TaxRuleView> rules = List.of(fixed(TaxCalcType.PER_NIGHT, "10000", "VND"),
				fixed(TaxCalcType.PER_PERSON_NIGHT, "5000", "VND"), fixed(TaxCalcType.PER_BOOKING, "30000", "VND"));

		// 2 rooms × 3 nights, 5 guests × 3 nights, once
		assertThat(TaxCalculator.taxes(stay(0), rules)).extracting(TaxLine::amountMinor)
			.containsExactly(60_000L, 75_000L, 30_000L);
	}

	@Test
	void aRuleTaxesOnlyTheNightsInsideItsValidity() {
		TaxRuleView endsAfterTheFirstNight = new TaxRuleView(1L, "Old VAT", TaxCalcType.PERCENT, new BigDecimal("10"),
				null, false, true, null, FIRST);
		TaxRuleView startsOnTheSecondNight = new TaxRuleView(2L, "New VAT", TaxCalcType.PERCENT, new BigDecimal("8"),
				null, false, true, FIRST.plusDays(1), null);
		TaxRuleView afterTheStay = new TaxRuleView(3L, "Later fee", TaxCalcType.PER_BOOKING, new BigDecimal("1"),
				"VND", false, true, FIRST.plusDays(3), null);

		assertThat(TaxCalculator.taxes(stay(0), List.of(endsAfterTheFirstNight, startsOnTheSecondNight, afterTheStay)))
			.containsExactly(new TaxLine(1L, "Old VAT", false, 200_000), new TaxLine(2L, "New VAT", false, 320_000));
	}

	@Test
	void eachRuleIsRoundedHalfUpOnceInMinorUnits() {
		// 3 nights of 33.33 USD = 99.99 USD; 7% = 699.93 cents; after a 0.01 USD discount 699.86 cents.
		List<Night> nights = List.of(new Night(FIRST, 3333), new Night(FIRST.plusDays(1), 3333),
				new Night(FIRST.plusDays(2), 3333));
		TaxRuleView tax = new TaxRuleView(1L, "Sales tax", TaxCalcType.PERCENT, new BigDecimal("7"), null, false, true,
				null, null);

		assertThat(TaxCalculator.taxes(new Stay("USD", nights, 1, 1, 0), List.of(tax)))
			.extracting(TaxLine::amountMinor).containsExactly(700L);
		assertThat(TaxCalculator.taxes(new Stay("USD", nights, 1, 1, 1), List.of(tax)))
			.extracting(TaxLine::amountMinor).containsExactly(700L);
		// A fixed 1.2345 USD a night for 1 room and 3 nights = 3.7035 USD = 370 cents.
		assertThat(TaxCalculator.taxes(new Stay("USD", nights, 1, 1, 0), List.of(fixed(TaxCalcType.PER_NIGHT,
				"1.2345", "USD")))).extracting(TaxLine::amountMinor).containsExactly(370L);
	}

	@Test
	void aFixedTaxInAnotherCurrencyRefusesTheBooking() {
		assertThatThrownBy(() -> TaxCalculator.taxes(stay(0), List.of(fixed(TaxCalcType.PER_NIGHT, "1", "USD"))))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT))
			.hasMessage("Taxes for this hotel can't be calculated yet");
	}

	/** 2 rooms, 5 guests, three nights of {@link #NIGHTS} in VND. */
	private static Stay stay(long discountMinor) {
		return new Stay("VND", NIGHTS, 2, 5, discountMinor);
	}

	private static TaxRuleView percent(String name, String value, boolean included, boolean afterDiscount) {
		return new TaxRuleView(1L, name, TaxCalcType.PERCENT, new BigDecimal(value), null, included, afterDiscount,
				null, null);
	}

	private static TaxRuleView fixed(TaxCalcType type, String value, String currency) {
		return new TaxRuleView(1L, type.name(), type, new BigDecimal(value), currency, false, true, null, null);
	}

}
