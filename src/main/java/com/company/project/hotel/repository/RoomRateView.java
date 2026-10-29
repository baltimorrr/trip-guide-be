package com.company.project.hotel.repository;

import java.util.List;

/**
 * One rate plan of the hotel page's room list that can sell the stay, in the hotel's currency.
 *
 * @param roomsLeft the room type's fewest free rooms on any night of the stay, shared by all its rate plans
 * @param fitsGuests whether the room type holds the guests of one room (the search's even split)
 * @param stayPriceMinor one room for the whole stay
 * @param nightPricesMinor one room's price per night, first night first
 */
public record RoomRateView(Long roomTypeId, Long ratePlanId, int roomsLeft, boolean fitsGuests, long stayPriceMinor,
		List<Long> nightPricesMinor) {
}
