package com.company.project.booking.dto;

import com.company.project.common.validation.IsoCountry;
import com.company.project.common.validation.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The person who books and is contacted about the booking. A quote fills it from the profile, where the phone and
 * country may be missing; a booking needs every field. The Swagger examples are the demo customer's profile.
 *
 * @param countryCode ISO 3166 alpha-2, any case
 */
public record BookerDetails(
		@Schema(example = "Demo Customer") @NotBlank @Size(max = 100) String fullName,
		@Schema(example = "demo@example.com") @NotBlank @Email @Size(max = 254) String email,
		@Schema(example = "+84901234567") @NotBlank @PhoneNumber String phone,
		@Schema(example = "VN") @NotBlank @IsoCountry String countryCode) {
}
