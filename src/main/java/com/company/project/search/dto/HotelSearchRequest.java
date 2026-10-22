package com.company.project.search.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Query parameters of a hotel search. Everything travels in the URL, so a search can be shared,
 * bookmarked, or stored by the browser as the "last search" and sent again unchanged.
 *
 * @param destination destination slug from the suggestions; give this or {@code hotel}
 * @param hotel hotel slug from the suggestions; give this or {@code destination}
 * @param childAges one age per child, repeated: {@code childAges=4&childAges=9}
 */
public record HotelSearchRequest(
		@Size(max = 200) String destination,
		@Size(max = 220) String hotel,
		@NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate checkIn,
		@NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate checkOut,
		@NotNull @Min(1) Integer rooms,
		@NotNull @Min(1) Integer adults,
		List<@NotNull @Min(0) @Max(17) Integer> childAges) {

	public HotelSearchRequest {
		childAges = childAges == null ? List.of() : childAges;
	}

}
