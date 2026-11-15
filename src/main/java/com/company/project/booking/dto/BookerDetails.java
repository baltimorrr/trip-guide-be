package com.company.project.booking.dto;

import com.company.project.common.validation.IsoCountry;
import com.company.project.common.validation.PhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The person who books and is contacted about the booking. A quote fills it from the profile, where the phone and
 * country may be missing; a booking needs every field.
 *
 * @param countryCode ISO 3166 alpha-2, any case
 */
public record BookerDetails(@NotBlank @Size(max = 100) String fullName,
		@NotBlank @Email @Size(max = 254) String email, @NotBlank @PhoneNumber String phone,
		@NotBlank @IsoCountry String countryCode) {
}
