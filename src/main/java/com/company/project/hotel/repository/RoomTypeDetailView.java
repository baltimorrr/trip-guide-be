package com.company.project.hotel.repository;

import java.math.BigDecimal;

/** What the hotel page's room list shows about a room type besides its beds, features and photos. */
public record RoomTypeDetailView(Long id, String name, String description, BigDecimal sizeSqm, short maxAdults,
		short maxChildren, short maxOccupancy) {
}
