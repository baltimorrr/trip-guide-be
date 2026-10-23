package com.company.project.user.dto;

import java.time.Instant;
import java.util.List;

import com.company.project.user.model.Role;

/**
 * The signed-in user's account and profile.
 *
 * @param countryCode nationality, ISO 3166 alpha-2
 * @param preferredCurrency ISO 4217
 */
public record UserResponse(Long id, String email, String fullName, String phone, String countryCode,
		String preferredCurrency, boolean emailVerified, List<Role> roles, Instant createdAt) {
}
