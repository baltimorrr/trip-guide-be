package com.company.project.search.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * Query parameters of a hotel search. Everything travels in the URL, so a search can be shared,
 * bookmarked, or stored by the browser as the "last search" and sent again unchanged. Lists are repeated
 * parameters ({@code stars=4&stars=5}); filters left out mean "any".
 *
 * @param destination destination slug from the suggestions; give this or {@code hotel}
 * @param hotel hotel slug from the suggestions; give this or {@code destination}
 * @param childAges one age per child, repeated: {@code childAges=4&childAges=9}
 * @param name part of the hotel name, ignoring accents and case
 * @param stars star ratings, any of them
 * @param minGoogleRating lowest Google rating (0–5)
 * @param minUserRating lowest user rating (0–10)
 * @param accommodationTypes accommodation type codes (hotel, resort...), any of them
 * @param facilities hotel facility codes (wifi, pool...), all of them
 * @param maxDistanceM farthest distance from the centre, in metres
 * @param boardTypes board type codes (RO, BB, HB, FB, AI), any of them; the offer then uses only those
 * @param cancellation FREE and/or NON_REFUNDABLE; the offer then uses only those rates
 * @param bedTypes bed type codes (single, double, queen, king, twin), any of them in the room
 * @param roomFeatures room feature codes (balcony, sea_view...), all of them in the room
 * @param minPrice lowest price per night of the whole offer, in {@code currency}
 * @param maxPrice highest price per night of the whole offer, in {@code currency}
 * @param currency ISO 4217 code that prices are compared in; default: the selling currency of most hotels in the
 * destination. Hotels selling in another currency never match a price range and come last when sorting by price.
 */
public record HotelSearchRequest(
		@Size(max = 200) String destination,
		@Size(max = 220) String hotel,
		@NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate checkIn,
		@NotNull @DateTimeFormat(iso = ISO.DATE) LocalDate checkOut,
		@NotNull @Min(1) Integer rooms,
		@NotNull @Min(1) Integer adults,
		List<@NotNull @Min(0) @Max(17) Integer> childAges,
		@Size(max = 100) String name,
		@Size(max = 5) List<@NotNull @Min(1) @Max(5) Integer> stars,
		@DecimalMin("0") @DecimalMax("5") BigDecimal minGoogleRating,
		@DecimalMin("0") @DecimalMax("10") BigDecimal minUserRating,
		@Size(max = 20) List<@NotBlank @Size(max = 30) String> accommodationTypes,
		@Size(max = 20) List<@NotBlank @Size(max = 50) String> facilities,
		@Positive Integer maxDistanceM,
		@Size(max = 20) List<@NotBlank @Size(max = 10) String> boardTypes,
		@Size(max = 2) List<@NotNull CancellationOption> cancellation,
		@Size(max = 20) List<@NotBlank @Size(max = 20) String> bedTypes,
		@Size(max = 20) List<@NotBlank @Size(max = 50) String> roomFeatures,
		@DecimalMin("0") @DecimalMax("1000000000000") BigDecimal minPrice,
		@DecimalMin("0") @DecimalMax("1000000000000") BigDecimal maxPrice,
		@Pattern(regexp = "[A-Z]{3}") String currency) {

	public HotelSearchRequest {
		childAges = childAges == null ? List.of() : childAges;
		stars = stars == null ? List.of() : stars;
		accommodationTypes = accommodationTypes == null ? List.of() : accommodationTypes;
		facilities = facilities == null ? List.of() : facilities;
		boardTypes = boardTypes == null ? List.of() : boardTypes;
		cancellation = cancellation == null ? List.of() : cancellation;
		bedTypes = bedTypes == null ? List.of() : bedTypes;
		roomFeatures = roomFeatures == null ? List.of() : roomFeatures;
	}

}
