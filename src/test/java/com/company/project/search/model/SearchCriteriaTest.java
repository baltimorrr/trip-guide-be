package com.company.project.search.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import com.company.project.common.exception.BusinessException;
import com.company.project.search.config.SearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SearchCriteriaTest {

	static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

	static final SearchProperties LIMITS = new SearchProperties(30, 365, 8, 30, 10, 10);

	@Test
	void acceptsAValidStayAndSplitsGuestsEvenly() {
		SearchCriteria criteria = of(TODAY, TODAY.plusDays(2), 2, 3, List.of(4));

		assertThat(criteria.children()).isEqualTo(1);
		assertThat(criteria.adultsPerRoom()).isEqualTo(2);
		assertThat(criteria.childrenPerRoom()).isEqualTo(1);
	}

	@Test
	void rejectsCheckInInThePast() {
		assertRejected(TODAY.minusDays(1), TODAY.plusDays(1), 1, 2, List.of(), "Check-in cannot be in the past");
	}

	@Test
	void rejectsCheckInTooFarAhead() {
		assertRejected(TODAY.plusDays(366), TODAY.plusDays(367), 1, 2, List.of(),
				"Check-in cannot be more than 365 days ahead");
	}

	@Test
	void rejectsCheckOutNotAfterCheckIn() {
		assertRejected(TODAY.plusDays(1), TODAY.plusDays(1), 1, 2, List.of(), "Check-out must be after check-in");
	}

	@Test
	void rejectsStaysLongerThanTheLimit() {
		assertThat(of(TODAY, TODAY.plusDays(30), 1, 2, List.of()).checkOut()).isEqualTo(TODAY.plusDays(30));
		assertRejected(TODAY, TODAY.plusDays(31), 1, 2, List.of(), "A stay cannot be longer than 30 nights");
	}

	@Test
	void rejectsTooManyRoomsAdultsOrChildren() {
		assertRejected(TODAY, TODAY.plusDays(1), 9, 9, List.of(), "Rooms cannot exceed 8");
		assertRejected(TODAY, TODAY.plusDays(1), 1, 31, List.of(), "Adults cannot exceed 30");
		assertRejected(TODAY, TODAY.plusDays(1), 1, 2, Collections.nCopies(11, 5), "Children cannot exceed 10");
	}

	@Test
	void everyRoomNeedsAnAdult() {
		assertRejected(TODAY, TODAY.plusDays(1), 2, 1, List.of(5), "Each room needs at least one adult");
	}

	private static SearchCriteria of(LocalDate checkIn, LocalDate checkOut, int rooms, int adults, List<Integer> ages) {
		return SearchCriteria.of(checkIn, checkOut, rooms, adults, ages, TODAY, LIMITS);
	}

	private static void assertRejected(LocalDate checkIn, LocalDate checkOut, int rooms, int adults,
			List<Integer> ages, String message) {
		assertThatThrownBy(() -> of(checkIn, checkOut, rooms, adults, ages))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
			.hasMessage(message);
	}

}
