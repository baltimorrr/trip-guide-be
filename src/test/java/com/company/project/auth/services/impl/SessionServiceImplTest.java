package com.company.project.auth.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.company.project.auth.config.AuthProperties;
import com.company.project.auth.dto.ClientInfo;
import com.company.project.auth.dto.TokenResponse;
import com.company.project.auth.model.UserSession;
import com.company.project.auth.repository.UserSessionRepository;
import com.company.project.auth.security.JwtProperties;
import com.company.project.auth.security.OpaqueTokens;
import com.company.project.auth.security.TokenService;
import com.company.project.common.exception.BusinessException;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

	@Mock
	UserSessionRepository sessions;

	@Mock
	TokenService tokenService;

	SessionServiceImpl service;

	final User user = withId(User.create("jane@example.com", "{noop}x", "Jane", null, Set.of(Role.USER), Instant.now()), 42L);

	@BeforeEach
	void setUp() {
		JwtProperties jwt = new JwtProperties("irrelevant", "spring-base", Duration.ofMinutes(15));
		AuthProperties auth = new AuthProperties("http://localhost:5173", Duration.ofDays(1), Duration.ofDays(30),
				Duration.ofHours(24), Duration.ofMinutes(30), Duration.ZERO,
				new AuthProperties.Lockout(5, Duration.ofMinutes(15), Duration.ofMinutes(15)));
		service = new SessionServiceImpl(sessions, tokenService, jwt, auth);
	}

	@Test
	void refreshRotatesAndKeepsThePreviousHash() {
		UserSession session = session("old", Instant.now().plus(Duration.ofDays(1)));
		when(sessions.findByTokenHashForUpdate(OpaqueTokens.hash("old"))).thenReturn(Optional.of(session));
		when(tokenService.issue(eq(user), eq(7L))).thenReturn("access");

		TokenResponse tokens = service.refresh("old");

		assertThat(tokens.accessToken()).isEqualTo("access");
		assertThat(session.getRefreshTokenHash()).isEqualTo(OpaqueTokens.hash(tokens.refreshToken()));
		assertThat(session.getPreviousTokenHash()).isEqualTo(OpaqueTokens.hash("old"));
		assertThat(session.getLastUsedAt()).isNotNull();
		assertThat(tokens.refreshExpiresIn()).isCloseTo(Duration.ofDays(1).toSeconds(), within(5L));
	}

	@Test
	void replayOfTheReplacedTokenRevokesTheSession() {
		UserSession session = session("old", Instant.now().plus(Duration.ofDays(1)));
		session.rotate(OpaqueTokens.hash("new"), Instant.now());
		when(sessions.findByTokenHashForUpdate(OpaqueTokens.hash("old"))).thenReturn(Optional.of(session));

		assertThatThrownBy(() -> service.refresh("old")).isInstanceOfSatisfying(BusinessException.class,
				ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
		assertThat(session.getRevokedAt()).isNotNull();
		verifyNoInteractions(tokenService);
	}

	@Test
	void endedSessionOrDisabledUserCannotRefresh() {
		UserSession ended = session("ended", Instant.now().minusSeconds(1));
		when(sessions.findByTokenHashForUpdate(OpaqueTokens.hash("ended"))).thenReturn(Optional.of(ended));
		User disabled = withId(User.create("off@example.com", "{noop}x", "Off", null, Set.of(Role.USER), Instant.now()), 43L);
		ReflectionTestUtils.setField(disabled, "enabled", false);
		UserSession ofDisabled = UserSession.create(disabled, OpaqueTokens.hash("off"), Instant.now().plusSeconds(60), null, null);
		when(sessions.findByTokenHashForUpdate(OpaqueTokens.hash("off"))).thenReturn(Optional.of(ofDisabled));
		when(sessions.findByTokenHashForUpdate(OpaqueTokens.hash("unknown"))).thenReturn(Optional.empty());

		for (String token : new String[] { "ended", "off", "unknown" }) {
			assertThatThrownBy(() -> service.refresh(token)).isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
		}
		assertThat(ofDisabled.getRevokedAt()).isNotNull();
		verifyNoInteractions(tokenService);
	}

	@Test
	void openStoresOnlyTheHashAndUsesTheRememberMeLifetime() {
		when(sessions.saveAndFlush(any(UserSession.class)))
			.thenAnswer(invocation -> withId(invocation.<UserSession>getArgument(0), 9L));
		when(tokenService.issue(user, 9L)).thenReturn("header.claims.signature");

		TokenResponse tokens = service.open(user, true,
				new ClientInfo("x".repeat(600), "10.0.0.1"));

		assertThat(tokens.expiresIn()).isEqualTo(900);
		assertThat(tokens.refreshExpiresIn()).isCloseTo(Duration.ofDays(30).toSeconds(), within(5L));
		assertThat(tokens.toString()).doesNotContain(tokens.refreshToken()).doesNotContain("header.claims.signature");
	}

	private UserSession session(String refreshToken, Instant expiresAt) {
		return withId(UserSession.create(user, OpaqueTokens.hash(refreshToken), expiresAt, "JUnit", "127.0.0.1"), 7L);
	}

	private static <T> T withId(T entity, long id) {
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

}
