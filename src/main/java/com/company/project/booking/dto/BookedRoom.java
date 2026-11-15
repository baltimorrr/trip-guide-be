package com.company.project.booking.dto;

import java.util.List;

import com.company.project.common.dto.Money;
import com.company.project.hotel.dto.RateCancellation;

/**
 * One room booked, with its guests.
 *
 * @param price this room for the whole stay
 * @param cancellation the rate's terms the booker accepted
 */
public record BookedRoom(Long roomTypeId, String roomName, Long ratePlanId, String ratePlanName, String boardType,
		int adults, List<Integer> childAges, String leadGuestName, Money price, RateCancellation cancellation) {
}
