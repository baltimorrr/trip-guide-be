package com.company.project.auth.security;

import java.time.Instant;

import com.company.project.auth.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Rejects an access token whose session was revoked (sign-out, password change, replayed refresh token) or has
 * ended, or whose user was disabled. One indexed lookup per authenticated request; this is what makes sign-out take
 * effect immediately instead of when the access token expires.
 */
@Component
@RequiredArgsConstructor
public class SessionTokenValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error INACTIVE_SESSION = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
			"Session is no longer active", null);

	private final UserSessionRepository sessions;

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		Long sessionId = sessionId(jwt);
		if (sessionId != null
				&& sessions.existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue(sessionId, Instant.now())) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(INACTIVE_SESSION);
	}

	/** The token's session id, or null when the claim is missing or malformed. */
	public static Long sessionId(Jwt jwt) {
		String sessionId = jwt.getClaimAsString(TokenService.SESSION_CLAIM);
		if (sessionId == null) {
			return null;
		}
		try {
			return Long.valueOf(sessionId);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
