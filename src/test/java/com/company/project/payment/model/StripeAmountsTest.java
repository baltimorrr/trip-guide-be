package com.company.project.payment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StripeAmountsTest {

	@Test
	void zeroAndTwoDecimalCurrenciesAreSentAsStored() {
		assertThat(StripeAmounts.toStripe(2_400_000, "VND")).isEqualTo(2_400_000);
		assertThat(StripeAmounts.toStripe(12_950, "USD")).isEqualTo(12_950);
		assertThat(StripeAmounts.toStripe(5_000, "JPY")).isEqualTo(5_000);
		assertThat(StripeAmounts.fromStripe(2_400_000, "VND")).isEqualTo(2_400_000);
		assertThat(StripeAmounts.fromStripe(12_950, "USD")).isEqualTo(12_950);
	}

	@Test
	void iskAndUgxAreCountedInHundredthsByStripe() {
		// 5 ISK is stored as 5 (ISO 4217 has no minor unit) and charged as 500.
		assertThat(StripeAmounts.toStripe(5, "ISK")).isEqualTo(500);
		assertThat(StripeAmounts.toStripe(70_000, "UGX")).isEqualTo(7_000_000);
		assertThat(StripeAmounts.fromStripe(500, "ISK")).isEqualTo(5);
		assertThat(StripeAmounts.fromStripe(7_000_000, "UGX")).isEqualTo(70_000);
	}

	@Test
	void threeDecimalCurrenciesNeedAnAmountInTens() {
		assertThat(StripeAmounts.toStripe(12_340, "KWD")).isEqualTo(12_340);
		assertThatThrownBy(() -> StripeAmounts.toStripe(12_345, "KWD"))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
		assertThat(StripeAmounts.fromStripe(12_340, "KWD")).isEqualTo(12_340);
	}

}
