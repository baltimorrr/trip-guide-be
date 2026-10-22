package com.company.project.hotel.repository;

import java.time.LocalDate;

/**
 * What an available hotel must offer: {@code rooms} rooms free on every night of {@code [checkIn, checkOut)},
 * each holding {@code adultsPerRoom} adults and {@code childrenPerRoom} children.
 */
public record StayRequirement(LocalDate checkIn, LocalDate checkOut, int rooms, int adultsPerRoom,
		int childrenPerRoom) {
}
