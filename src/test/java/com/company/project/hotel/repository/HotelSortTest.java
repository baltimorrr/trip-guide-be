package com.company.project.hotel.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

class HotelSortTest {

	@Test
	void noSortMeansTheDefaultOrder() {
		assertThat(HotelSort.from(Sort.unsorted())).isNull();
	}

	@Test
	void readsTheNameInAnyCaseAndIgnoresTheDirection() {
		assertThat(HotelSort.from(Sort.by("Price"))).isEqualTo(HotelSort.PRICE);
		assertThat(HotelSort.from(Sort.by(Direction.DESC, "rating"))).isEqualTo(HotelSort.RATING);
		assertThat(HotelSort.from(Sort.by("DISTANCE"))).isEqualTo(HotelSort.DISTANCE);
	}

	@Test
	void rejectsUnknownNamesAndSeveralSorts() {
		assertThatThrownBy(() -> HotelSort.from(Sort.by("recommended"))).isInstanceOf(BusinessException.class)
			.hasMessage("Sort by one of: price, rating, distance");
		assertThatThrownBy(() -> HotelSort.from(Sort.by("price", "rating"))).isInstanceOf(BusinessException.class);
	}

}
