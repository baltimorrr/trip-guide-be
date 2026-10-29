package com.company.project.hotel.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The rooms a hotel can sell for the stay (C-HOTEL-04). Prices are for one room, in the hotel's {@code currency}.
 *
 * @param taxesIncluded true when the hotel's country adds no tax on top of room prices for these nights; the amounts
 * come with the checkout price breakdown
 * @param roomTypes the room types that fit the guests first, then the others, cheapest rate first
 */
public record RoomList(LocalDate checkIn, LocalDate checkOut, int nights, int rooms, int adults, int children,
		String currency, boolean taxesIncluded, List<RoomOption> roomTypes) {
}
