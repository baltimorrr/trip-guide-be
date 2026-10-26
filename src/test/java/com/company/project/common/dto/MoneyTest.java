package com.company.project.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

	@Test
	void scalesMinorUnitsByTheCurrencysDecimalDigits() {
		assertThat(Money.ofMinor(2_400_000, "VND")).isEqualTo(new Money(new BigDecimal("2400000"), "VND"));
		assertThat(Money.ofMinor(12_950, "USD")).isEqualTo(new Money(new BigDecimal("129.50"), "USD"));
		assertThat(Money.ofMinor(0, "USD").amount()).isEqualByComparingTo("0");
	}

	@Test
	void convertsMajorUnitsToWholeMinorUnits() {
		assertThat(Money.toMinor(new BigDecimal("2400000"), "VND")).isEqualTo(2_400_000);
		assertThat(Money.toMinor(new BigDecimal("129.5"), "USD")).isEqualTo(12_950);
		assertThat(Money.toMinor(new BigDecimal("129.505"), "USD")).isEqualTo(12_951);
		assertThat(Money.toMinor(new BigDecimal("0.4"), "VND")).isZero();
	}

}
