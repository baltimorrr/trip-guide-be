package com.company.project.auth.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.company.project.auth.config.AuthProperties;
import com.company.project.auth.dto.ChangePasswordRequest;
import com.company.project.auth.dto.ResetPasswordRequest;
import com.company.project.auth.mail.AuthMailer;
import com.company.project.auth.model.TokenType;
import com.company.project.auth.services.SessionService;
import com.company.project.auth.services.UserTokenService;
import com.company.project.common.exception.BusinessException;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import com.company.project.user.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordServiceImplTest {

	@Mock
	UserRepository users;

	@Mock
	UserService userService;

	@Mock
	UserTokenService userTokens;

	@Mock
	SessionService sessionService;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	AuthMailer mailer;

	PasswordServiceImpl service;

	final User user = user(42L);

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties("https://app.example.com", Duration.ofDays(1), Duration.ofDays(30),
				Duration.ofHours(24), Duration.ofMinutes(30), Duration.ZERO,
				new AuthProperties.Lockout(5, Duration.ofMinutes(15), Duration.ofMinutes(15)));
		service = new PasswordServiceImpl(users, userService, userTokens, sessionService, passwordEncoder, mailer, properties);
	}

	@Test
	void forgotPasswordMailsALinkToTheFrontend() {
		when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
		when(userTokens.issue(user, TokenType.PASSWORD_RESET)).thenReturn(Optional.of("tok"));

		service.forgotPassword(" Jane@Example.com ");

		verify(mailer).sendPasswordReset("jane@example.com", "https://app.example.com/reset-password?token=tok");
	}

	@Test
	void forgotPasswordForADisabledAccountSendsNothing() {
		ReflectionTestUtils.setField(user, "enabled", false);
		when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

		service.forgotPassword("jane@example.com");

		verifyNoInteractions(userTokens, mailer);
	}

	@Test
	void resetForADisabledAccountIsRejected() {
		ReflectionTestUtils.setField(user, "enabled", false);
		when(userTokens.consume("tok", TokenType.PASSWORD_RESET)).thenReturn(user);

		assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("tok", "new-password-2")))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		verifyNoInteractions(userService, sessionService);
	}

	@Test
	void resetVerifiesTheEmailAndSignsOutEverywhere() {
		when(userTokens.consume("tok", TokenType.PASSWORD_RESET)).thenReturn(user);

		service.resetPassword(new ResetPasswordRequest("tok", "new-password-2"));

		verify(userService).changePassword(user, "new-password-2");
		verify(sessionService).revokeAll(42L);
		assertThat(user.isEmailVerified()).isTrue();
	}

	@Test
	void wrongCurrentPasswordNeitherChangesNorSignsOut() {
		when(users.findById(42L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("guess", user.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> service.changePassword(42L, 7L, new ChangePasswordRequest("guess", "new-password-2")))
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
		verify(userService, never()).changePassword(any(), any());
		verifyNoInteractions(sessionService);
	}

	@Test
	void overlongCurrentPasswordIsWrongWithoutAskingBcrypt() {
		when(users.findById(42L)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.changePassword(42L, 7L, new ChangePasswordRequest("é".repeat(37), "new-password-2")))
			.isInstanceOf(BusinessException.class);
		verifyNoInteractions(passwordEncoder, sessionService);
	}

	@Test
	void changePasswordKeepsTheCurrentSession() {
		when(users.findById(42L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current-1", user.getPasswordHash())).thenReturn(true);

		service.changePassword(42L, 7L, new ChangePasswordRequest("current-1", "new-password-2"));

		verify(userService).changePassword(user, "new-password-2");
		verify(sessionService).revokeAllExcept(42L, 7L);
	}

	private static User user(long id) {
		User user = User.create("jane@example.com", "{bcrypt}hash", "Jane", null, Set.of(Role.USER), null);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
