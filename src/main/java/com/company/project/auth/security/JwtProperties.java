package com.company.project.auth.security;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings under {@code app.jwt}.
 *
 * @param secret base64 of at least 32 random bytes ({@code openssl rand -base64 32})
 * @param issuer value of the {@code iss} claim; tokens with another issuer are rejected
 * @param accessTokenTtl how long an access token stays valid
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {

	@Override
	public String toString() {
		return "JwtProperties[secret=***, issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + "]";
	}

}
