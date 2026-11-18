package com.company.project.hotel.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * The stay a hotel page prices its rooms for: the same parameters, with the same rules, as a hotel search, so a
 * search result's link passes them on unchanged.
 *
 * @param childAges one age per child, repeated: {@code childAges=4&childAges=9}
 */
public record StayRequest(
		@NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate checkIn,
		@NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate checkOut,
		@Parameter(example = "1") @NotNull @Min(1) Integer rooms,
		@Parameter(example = "2") @NotNull @Min(1) Integer adults,
		List<@NotNull @Min(0) @Max(17) Integer> childAges) {

	public StayRequest {
		childAges = childAges == null ? List.of() : childAges;
	}

}
