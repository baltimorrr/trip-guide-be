package com.company.project.auth.services.impl;

import java.time.Instant;
import java.util.Set;

import com.company.project.auth.config.AuthProperties;
import com.company.project.auth.dto.ClientInfo;
import com.company.project.auth.dto.LoginRequest;
import com.company.project.auth.dto.RegisterRequest;
import com.company.project.auth.dto.TokenResponse;
import com.company.project.auth.mail.AuthMailer;
import com.company.project.auth.model.TokenType;
import com.company.project.auth.services.AuthService;
import com.company.project.auth.services.SessionService;
import com.company.project.auth.services.UserTokenService;
import com.company.project.common.exception.BusinessException;
import com.company.project.user.dto.UserResponse;
import com.company.project.user.mapper.UserMapper;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import com.company.project.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	static final String VERIFY_EMAIL_PATH = "/verify-email";

	private final UserService userService;

	private final UserRepository users;

	private final AuthenticationManager authenticationManager;

	private final SessionService sessionService;

	private final UserTokenService userTokens;

	private final AuthMailer mailer;

	private final AuthProperties properties;

	private final UserMapper userMapper;

	/**
	 * Not one transaction: the account and its link commit first and the email goes out after, never from inside
	 * a transaction. If the link fails to save, resending the verification email recovers the account.
	 */
	@Override
	public UserResponse register(RegisterRequest request) {
		User user = userService.create(request.email(), request.password(), request.fullName(), request.phone(),
				Set.of(Role.USER), false);
		userTokens.issue(user, TokenType.EMAIL_VERIFY).ifPresent(token -> sendVerification(user, token));
		return userMapper.toResponse(user);
	}

	@Override
	@Transactional
	public void verifyEmail(String token) {
		userTokens.consume(token, TokenType.EMAIL_VERIFY).verifyEmail(Instant.now());
	}

	@Override
	public void resendVerification(String email) {
		users.findByEmail(User.normalizeEmail(email))
			.filter(user -> user.isEnabled() && !user.isEmailVerified())
			.ifPresent(user -> userTokens.issue(user, TokenType.EMAIL_VERIFY).ifPresent(token -> sendVerification(user, token)));
	}

	/**
	 * Not transactional: a failed attempt is counted in its own transaction, which must commit even though the
	 * 401 is thrown right after.
	 */
	@Override
	public TokenResponse login(LoginRequest request, ClientInfo client) {
		String email = User.normalizeEmail(request.email());
		// No stored password can be longer, and BCrypt would reject it rather than answer "no match".
		if (UserService.exceedsPasswordByteLimit(request.password())) {
			recordFailedLogin(email);
			throw new BadCredentialsException("Bad credentials");
		}
		try {
			authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
		}
		catch (BadCredentialsException ex) {
			// Wrong password or unknown email (counting an unknown email updates nothing). A locked account throws
			// LockedException instead and is not counted again.
			recordFailedLogin(email);
			throw ex;
		}
		User user = users.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Bad credentials"));
		// Only reached with the right password, so this reveals nothing to someone guessing emails.
		if (!user.isEmailVerified()) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "Email not verified");
		}
		if (user.getFailedLoginCount() > 0 || user.getLockedUntil() != null) {
			users.clearFailedLogins(user.getId(), Instant.now());
		}
		return sessionService.open(user, request.isRememberMe(), client);
	}

	@Override
	public TokenResponse refresh(String refreshToken) {
		return sessionService.refresh(refreshToken);
	}

	@Override
	public void logout(String refreshToken) {
		sessionService.revoke(refreshToken);
	}

	@Override
	public void logoutAll(Long userId) {
		sessionService.revokeAll(userId);
	}

	private void recordFailedLogin(String email) {
		Instant now = Instant.now();
		AuthProperties.Lockout lockout = properties.lockout();
		users.recordFailedLogin(email, now, now.minus(lockout.window()), lockout.maxAttempts(), now.plus(lockout.duration()));
	}

	private void sendVerification(User user, String token) {
		mailer.sendEmailVerification(user.getEmail(), properties.link(VERIFY_EMAIL_PATH, token));
	}

}
