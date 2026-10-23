package com.company.project.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.company.project.auth.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class SessionTokenValidatorTest {

	@Mock
	UserSessionRepository sessions;

	@InjectMocks
	SessionTokenValidator validator;

	@Test
	void liveSessionPasses() {
		when(sessions.existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue(eq(7L), any())).thenReturn(true);

		assertThat(validator.validate(token("7")).hasErrors()).isFalse();
	}

	@Test
	void revokedEndedOrDisabledSessionFails() {
		when(sessions.existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue(eq(7L), any())).thenReturn(false);

		assertThat(validator.validate(token("7")).getErrors())
			.singleElement()
			.satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
	}

	@Test
	void tokenWithoutAUsableSessionIdFailsWithoutALookup() {
		assertThat(validator.validate(token(null)).hasErrors()).isTrue();
		assertThat(validator.validate(token("not-a-number")).hasErrors()).isTrue();
		verifyNoInteractions(sessions);
	}

	private static Jwt token(String sessionId) {
		Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256").subject("1").issuedAt(Instant.now());
		if (sessionId != null) {
			builder.claim(TokenService.SESSION_CLAIM, sessionId);
		}
		return builder.build();
	}

}
