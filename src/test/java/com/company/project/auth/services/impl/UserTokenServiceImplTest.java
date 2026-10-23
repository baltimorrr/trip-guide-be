package com.company.project.auth.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.company.project.auth.config.AuthProperties;
import com.company.project.auth.model.TokenType;
import com.company.project.auth.model.UserToken;
import com.company.project.auth.repository.UserTokenRepository;
import com.company.project.auth.security.OpaqueTokens;
import com.company.project.common.exception.BusinessException;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserTokenServiceImplTest {

	@Mock
	UserTokenRepository tokens;

	final User user = user(42L);

	@Test
	void linkInsideTheCooldownIsNotSent() {
		when(tokens.existsByUserIdAndTypeAndCreatedAtAfter(eq(42L), eq(TokenType.EMAIL_VERIFY), any())).thenReturn(true);

		assertThat(service(Duration.ofMinutes(1)).issue(user, TokenType.EMAIL_VERIFY)).isEmpty();
		verify(tokens, never()).save(any());
		verify(tokens, never()).invalidateUnused(any(), any(), any());
	}

	@Test
	void newLinkReplacesOlderOnesAndStoresOnlyTheHash() {
		Instant before = Instant.now();

		String token = service(Duration.ZERO).issue(user, TokenType.PASSWORD_RESET).orElseThrow();

		verify(tokens).invalidateUnused(eq(42L), eq(TokenType.PASSWORD_RESET), any());
		ArgumentCaptor<UserToken> saved = ArgumentCaptor.forClass(UserToken.class);
		verify(tokens).save(saved.capture());
		assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(token)).isNotEqualTo(token);
		assertThat(saved.getValue().getType()).isEqualTo(TokenType.PASSWORD_RESET);
		assertThat(saved.getValue().getExpiresAt()).isBetween(before.plus(Duration.ofMinutes(30)),
				Instant.now().plus(Duration.ofMinutes(30)));
	}

	@Test
	void consumeMarksTheLinkUsedAndReturnsItsUser() {
		UserToken stored = UserToken.create(user, TokenType.EMAIL_VERIFY, OpaqueTokens.hash("t"), Instant.now().plusSeconds(60));
		when(tokens.findByTokenHashForUpdate(OpaqueTokens.hash("t"))).thenReturn(Optional.of(stored));

		assertThat(service(Duration.ZERO).consume("t", TokenType.EMAIL_VERIFY)).isSameAs(user);
		assertThat(stored.getUsedAt()).isNotNull();
	}

	@Test
	void consumeRejectsWrongTypeUsedExpiredAndUnknownLinks() {
		Instant now = Instant.now();
		UserToken resetLink = UserToken.create(user, TokenType.PASSWORD_RESET, "a", now.plusSeconds(60));
		UserToken used = UserToken.create(user, TokenType.EMAIL_VERIFY, "b", now.plusSeconds(60));
		used.markUsed(now);
		UserToken expired = UserToken.create(user, TokenType.EMAIL_VERIFY, "c", now.minusSeconds(1));
		when(tokens.findByTokenHashForUpdate(OpaqueTokens.hash("reset"))).thenReturn(Optional.of(resetLink));
		when(tokens.findByTokenHashForUpdate(OpaqueTokens.hash("used"))).thenReturn(Optional.of(used));
		when(tokens.findByTokenHashForUpdate(OpaqueTokens.hash("expired"))).thenReturn(Optional.of(expired));
		when(tokens.findByTokenHashForUpdate(OpaqueTokens.hash("unknown"))).thenReturn(Optional.empty());

		for (String token : new String[] { "reset", "used", "expired", "unknown" }) {
			assertThatThrownBy(() -> service(Duration.ZERO).consume(token, TokenType.EMAIL_VERIFY))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		}
		assertThat(resetLink.getUsedAt()).isNull();
	}

	private UserTokenServiceImpl service(Duration cooldown) {
		AuthProperties properties = new AuthProperties("http://localhost:5173", Duration.ofDays(1), Duration.ofDays(30),
				Duration.ofHours(24), Duration.ofMinutes(30), cooldown,
				new AuthProperties.Lockout(5, Duration.ofMinutes(15), Duration.ofMinutes(15)));
		return new UserTokenServiceImpl(tokens, properties);
	}

	private static User user(long id) {
		User user = User.create("jane@example.com", "{noop}irrelevant", "Jane", null, Set.of(Role.USER), null);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
