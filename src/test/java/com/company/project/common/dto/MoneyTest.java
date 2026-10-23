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

}
