package com.company.project.auth.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Account settings under {@code app.auth}.
 *
 * @param frontendUrl base URL of the web app; emailed links open a page there, which posts the token to the API
 * @param refreshTokenTtl how long a sign-in lasts without "remember me"
 * @param rememberMeTtl how long a sign-in lasts with "remember me"
 * @param emailVerificationTtl how long a verification link works
 * @param passwordResetTtl how long a password reset link works
 * @param emailCooldown minimum time between two links of one type to one account
 * @param lockout when repeated failed sign-ins lock an account
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(@NotBlank String frontendUrl, @NotNull Duration refreshTokenTtl,
		@NotNull Duration rememberMeTtl, @NotNull Duration emailVerificationTtl, @NotNull Duration passwordResetTtl,
		@NotNull Duration emailCooldown, @Valid @NotNull Lockout lockout) {

	/**
	 * @param maxAttempts failures within {@code window} that lock the account
	 * @param window counted from the first failure
	 * @param duration how long the lock lasts
	 */
	public record Lockout(@Min(2) int maxAttempts, @NotNull Duration window, @NotNull Duration duration) {
	}

	/** A frontend link carrying a one-time token, e.g. {@code https://app.example.com/verify-email?token=...}. */
	public String link(String path, String token) {
		return UriComponentsBuilder.fromUriString(frontendUrl).path(path).queryParam("token", token).build().toUriString();
	}

}
