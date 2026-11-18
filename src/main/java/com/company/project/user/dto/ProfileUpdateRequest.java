package com.company.project.user.dto;

import com.company.project.common.validation.IsoCountry;
import com.company.project.common.validation.IsoCurrency;
import com.company.project.common.validation.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Replaces the whole profile: an optional field left out is cleared. Saved details pre-fill the guest form at
 * checkout. The Swagger examples are the demo customer's own profile.
 *
 * @param countryCode nationality, ISO 3166 alpha-2, any case
 * @param preferredCurrency ISO 4217, any case
 */
public record ProfileUpdateRequest(
		@Schema(example = "Demo Customer") @NotBlank @Size(max = 100) String fullName,
		@Schema(example = "+84901234567") @PhoneNumber String phone,
		@Schema(example = "VN") @IsoCountry String countryCode,
		@Schema(example = "VND") @IsoCurrency String preferredCurrency) {
}
