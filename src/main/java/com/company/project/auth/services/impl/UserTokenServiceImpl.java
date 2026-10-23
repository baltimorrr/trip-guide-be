package com.company.project.auth.services.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.company.project.auth.config.AuthProperties;
import com.company.project.auth.model.TokenType;
import com.company.project.auth.model.UserToken;
import com.company.project.auth.repository.UserTokenRepository;
import com.company.project.auth.security.OpaqueTokens;
import com.company.project.auth.services.UserTokenService;
import com.company.project.common.exception.BusinessException;
import com.company.project.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserTokenServiceImpl implements UserTokenService {

	private final UserTokenRepository tokens;

	private final AuthProperties properties;

	@Override
	@Transactional
	public Optional<String> issue(User user, TokenType type) {
		Instant now = Instant.now();
		Duration cooldown = properties.emailCooldown();
		if (cooldown.isPositive() && tokens.existsByUserIdAndTypeAndCreatedAtAfter(user.getId(), type, now.minus(cooldown))) {
			return Optional.empty();
		}
		tokens.invalidateUnused(user.getId(), type, now);
		String token = OpaqueTokens.generate();
		tokens.save(UserToken.create(user, type, OpaqueTokens.hash(token), now.plus(lifetime(type))));
		return Optional.of(token);
	}

	@Override
	@Transactional
	public User consume(String token, TokenType type) {
		Instant now = Instant.now();
		UserToken stored = tokens.findByTokenHashForUpdate(OpaqueTokens.hash(token))
			.filter(found -> found.getType() == type && found.isUsable(now))
			.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "Invalid or expired link"));
		stored.markUsed(now);
		return stored.getUser();
	}

	private Duration lifetime(TokenType type) {
		return switch (type) {
			case EMAIL_VERIFY -> properties.emailVerificationTtl();
			case PASSWORD_RESET -> properties.passwordResetTtl();
		};
	}

}
