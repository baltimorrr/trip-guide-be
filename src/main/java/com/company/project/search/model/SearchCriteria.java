package com.company.project.search.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.company.project.common.exception.BusinessException;
import com.company.project.search.config.SearchProperties;
import org.springframework.http.HttpStatus;

/**
 * Stay and guests of a hotel search, checked against the rules Bean Validation cannot express
 * (dates relative to today, limits, adults per room). Construct with {@link #of}.
 * <p>
 * Guests are split evenly over the rooms: a room type fits when it can hold
 * {@link #adultsPerRoom()} adults and {@link #childrenPerRoom()} children.
 */
public record SearchCriteria(LocalDate checkIn, LocalDate checkOut, int rooms, int adults, List<Integer> childAges) {

	/** @throws BusinessException 400 with a message naming the first rule broken */
	public static SearchCriteria of(LocalDate checkIn, LocalDate checkOut, int rooms, int adults,
			List<Integer> childAges, LocalDate today, SearchProperties limits) {
		if (checkIn.isBefore(today)) {
			throw invalid("Check-in cannot be in the past");
		}
		if (checkIn.isAfter(today.plusDays(limits.maxDaysAhead()))) {
			throw invalid("Check-in cannot be more than %d days ahead".formatted(limits.maxDaysAhead()));
		}
		if (!checkOut.isAfter(checkIn)) {
			throw invalid("Check-out must be after check-in");
		}
		if (ChronoUnit.DAYS.between(checkIn, checkOut) > limits.maxNights()) {
			throw invalid("A stay cannot be longer than %d nights".formatted(limits.maxNights()));
		}
		if (rooms > limits.maxRooms()) {
			throw invalid("Rooms cannot exceed %d".formatted(limits.maxRooms()));
		}
		if (adults > limits.maxAdults()) {
			throw invalid("Adults cannot exceed %d".formatted(limits.maxAdults()));
		}
		if (childAges.size() > limits.maxChildren()) {
			throw invalid("Children cannot exceed %d".formatted(limits.maxChildren()));
		}
		// Every booked room needs an adult (cart and booking rows require adults >= 1 per room).
		if (adults < rooms) {
			throw invalid("Each room needs at least one adult");
		}
		return new SearchCriteria(checkIn, checkOut, rooms, adults, List.copyOf(childAges));
	}

	public int children() {
		return childAges.size();
	}

	public int adultsPerRoom() {
		return Math.ceilDiv(adults, rooms);
	}

	public int childrenPerRoom() {
		return Math.ceilDiv(children(), rooms);
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(HttpStatus.BAD_REQUEST, message);
	}

}
