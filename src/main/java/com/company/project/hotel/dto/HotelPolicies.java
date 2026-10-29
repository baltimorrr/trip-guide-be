package com.company.project.hotel.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * What a guest should know before arriving. Times are wall-clock times in the hotel's own time zone.
 *
 * @param checkInUntil latest check-in, null when there is none
 * @param minCheckInAge null when the hotel sets none
 * @param rules the hotel's policy paragraphs in its own order
 */
public record HotelPolicies(LocalTime checkInFrom, LocalTime checkInUntil, LocalTime checkOutUntil,
		Short minCheckInAge, List<HotelPolicyItem> rules) {
}
