package com.company.project.booking.dto;

import java.time.LocalTime;
import java.util.List;

import com.company.project.common.dto.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Books one cart group, as quoted.
 *
 * @param expectedTotal the quote's {@code price.total}: the total the customer agreed to pay
 * @param guestName the main guest when booking for someone else
 * @param specialRequests passed to the hotel, which may not be able to meet them
 * @param estimatedArrival the hotel's local time, e.g. "18:30"
 * @param leadGuests optional lead guest names per room
 * @param acceptedTerms the terms, the privacy policy and the rates' cancellation policies; must be true
 */
public record BookingRequest(@NotEmpty @Size(max = 20) List<@NotNull Long> cartItemIds,
		@Size(max = 50) String discountCode, @NotNull @Valid Money expectedTotal, @NotNull @Valid BookerDetails booker,
		@Size(max = 100) String guestName, @Size(max = 1000) String specialRequests, LocalTime estimatedArrival,
		@Size(max = 20) List<@NotNull @Valid LeadGuests> leadGuests, @NotNull @AssertTrue Boolean acceptedTerms) {

	public BookingRequest {
		leadGuests = leadGuests == null ? List.of() : leadGuests;
	}

}
