package com.company.project.booking.dto;

import java.time.LocalTime;
import java.util.List;

import com.company.project.common.dto.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Books one cart group, as quoted. In Swagger, send the same {@code cartItemIds} and {@code discountCode} as the
 * quote and replace {@code expectedTotal} with its {@code price.total}; any other total answers 409.
 *
 * @param expectedTotal the quote's {@code price.total}: the total the customer agreed to pay
 * @param guestName the main guest when booking for someone else
 * @param specialRequests passed to the hotel, which may not be able to meet them
 * @param estimatedArrival the hotel's local time, e.g. "18:30"
 * @param leadGuests optional lead guest names per room
 * @param acceptedTerms the terms, the privacy policy and the rates' cancellation policies; must be true
 */
public record BookingRequest(
		@Schema(example = "[1]") @NotEmpty @Size(max = 20) List<@NotNull Long> cartItemIds,
		@Schema(example = "welcome10") @Size(max = 50) String discountCode,
		@Schema(description = "the quote's price.total") @NotNull @Valid Money expectedTotal,
		@NotNull @Valid BookerDetails booker,
		@Schema(example = "Demo Customer") @Size(max = 100) String guestName,
		@Schema(example = "A quiet room, please.") @Size(max = 1000) String specialRequests,
		@Schema(type = "string", example = "18:30") LocalTime estimatedArrival,
		@Schema(example = "[]") @Size(max = 20) List<@NotNull @Valid LeadGuests> leadGuests,
		@Schema(example = "true") @NotNull @AssertTrue Boolean acceptedTerms) {

	public BookingRequest {
		leadGuests = leadGuests == null ? List.of() : leadGuests;
	}

}
