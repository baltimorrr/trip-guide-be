package com.company.project.user.dto;

import com.company.project.common.validation.IsoCountry;
import com.company.project.common.validation.IsoCurrency;
import com.company.project.common.validation.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Replaces the whole profile: an optional field left out is cleared. Saved details pre-fill the guest form at
 * checkout.
 *
 * @param countryCode nationality, ISO 3166 alpha-2, any case
 * @param preferredCurrency ISO 4217, any case
 */
public record ProfileUpdateRequest(@NotBlank @Size(max = 100) String fullName, @PhoneNumber String phone,
		@IsoCountry String countryCode, @IsoCurrency String preferredCurrency) {
}
