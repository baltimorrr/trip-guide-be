package com.company.project.hotel.repository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * What an available hotel must offer: {@code rooms} rooms free on every night of {@code [checkIn, checkOut)},
 * each holding {@code adultsPerRoom} adults and {@code childrenPerRoom} children.
 */
public record StayRequirement(LocalDate checkIn, LocalDate checkOut, int rooms, int adultsPerRoom,
		int childrenPerRoom) {

	public int nights() {
		return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
	}

}
