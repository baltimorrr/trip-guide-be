package com.company.project.hotel.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A room type with a room free on every night of the stay and every rate it can be sold on.
 *
 * @param sizeSqm null when not known
 * @param roomsLeft rooms free on every night, shared by all of its rates: the most a guest can pick of this room type
 * @param fitsGuests whether one room holds the guests of one room when they are split evenly over the rooms; a room
 * type that doesn't can still be part of an uneven mix
 * @param rates cheapest first
 */
public record RoomOption(Long roomTypeId, String name, String description, BigDecimal sizeSqm, int maxAdults,
		int maxChildren, int maxOccupancy, List<RoomBed> beds, List<RoomFeature> features, List<String> photoUrls,
		int roomsLeft, boolean fitsGuests, List<RateOption> rates) {
}
