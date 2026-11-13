package com.company.project.auth.services.impl;

import java.time.Instant;

import com.company.project.auth.config.AuthProperties;
import com.company.project.auth.dto.ChangePasswordRequest;
import com.company.project.auth.dto.ResetPasswordRequest;
import com.company.project.auth.model.TokenType;
import com.company.project.auth.services.AuthMailService;
import com.company.project.auth.services.PasswordService;
import com.company.project.auth.services.SessionService;
import com.company.project.auth.services.UserTokenService;
import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import com.company.project.user.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordServiceImpl implements PasswordService {

	static final String RESET_PASSWORD_PATH = "/reset-password";

	private final UserRepository users;

	private final UserService userService;

	private final UserTokenService userTokens;

	private final SessionService sessionService;

	private final PasswordEncoder passwordEncoder;

	private final AuthMailService mailService;

	private final AuthProperties properties;

	/** Not one transaction: the link commits first and the email goes out after. */
	@Override
	public void forgotPassword(String email) {
		users.findByEmail(User.normalizeEmail(email))
			.filter(User::isEnabled)
			.ifPresent(user -> userTokens.issue(user, TokenType.PASSWORD_RESET)
				.ifPresent(token -> mailService.sendPasswordReset(user.getEmail(), properties.link(RESET_PASSWORD_PATH, token))));
	}

	@Override
	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		User user = userTokens.consume(request.token(), TokenType.PASSWORD_RESET);
		// Rolls back the consumed link too, like any failure here.
		if (!user.isEnabled()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid or expired link");
		}
		userService.changePassword(user, request.newPassword());
		user.verifyEmail(Instant.now());
		sessionService.revokeAll(user.getId());
	}

	@Override
	@Transactional
	public void changePassword(Long userId, Long currentSessionId, ChangePasswordRequest request) {
		User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
		// An over-long password cannot match, and BCrypt would reject it rather than answer "no match".
		if (UserService.exceedsPasswordByteLimit(request.currentPassword())
				|| !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
		}
		userService.changePassword(user, request.newPassword());
		sessionService.revokeAllExcept(userId, currentSessionId);
	}

}
