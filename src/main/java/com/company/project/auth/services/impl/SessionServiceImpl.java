package com.company.project.auth.services.impl;

import java.time.Duration;
import java.time.Instant;

import com.company.project.auth.config.AuthProperties;
import com.company.project.auth.dto.ClientInfo;
import com.company.project.auth.dto.TokenResponse;
import com.company.project.auth.model.UserSession;
import com.company.project.auth.repository.UserSessionRepository;
import com.company.project.auth.security.JwtProperties;
import com.company.project.auth.security.OpaqueTokens;
import com.company.project.auth.security.TokenService;
import com.company.project.auth.services.SessionService;
import com.company.project.common.exception.BusinessException;
import com.company.project.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

	private final UserSessionRepository sessions;

	private final TokenService tokenService;

	private final JwtProperties jwtProperties;

	private final AuthProperties authProperties;

	@Override
	@Transactional
	public TokenResponse open(User user, boolean rememberMe, ClientInfo client) {
		Instant now = Instant.now();
		Duration lifetime = rememberMe ? authProperties.rememberMeTtl() : authProperties.refreshTokenTtl();
		String refreshToken = OpaqueTokens.generate();
		UserSession session = sessions.saveAndFlush(UserSession.create(user, OpaqueTokens.hash(refreshToken),
				now.plus(lifetime), client.userAgent(), client.ip()));
		return tokens(user, session, refreshToken, now);
	}

	/**
	 * Revoking a session and then answering 401 must commit the revocation, hence {@code noRollbackFor}: otherwise a
	 * replayed token would be rejected but the thief's newer token would keep working. The revocation is the only
	 * write before any BusinessException here.
	 */
	@Override
	@Transactional(noRollbackFor = BusinessException.class)
	public TokenResponse refresh(String refreshToken) {
		Instant now = Instant.now();
		String hash = OpaqueTokens.hash(refreshToken);
		// Locks the row: a second refresh with the same token waits, then sees it as already exchanged.
		UserSession session = sessions.findByTokenHashForUpdate(hash).orElseThrow(SessionServiceImpl::invalidRefreshToken);
		if (!hash.equals(session.getRefreshTokenHash())) {
			session.revoke(now);
			log.warn("Refresh token reused: session {} of user {} revoked", session.getId(), session.getUser().getId());
			throw invalidRefreshToken();
		}
		User user = session.getUser();
		if (!session.isActive(now) || !user.isEnabled()) {
			session.revoke(now);
			throw invalidRefreshToken();
		}
		String nextRefreshToken = OpaqueTokens.generate();
		session.rotate(OpaqueTokens.hash(nextRefreshToken), now);
		return tokens(user, session, nextRefreshToken, now);
	}

	@Override
	@Transactional
	public void revoke(String refreshToken) {
		Instant now = Instant.now();
		sessions.findActiveByTokenHash(OpaqueTokens.hash(refreshToken), now).ifPresent(session -> session.revoke(now));
	}

	@Override
	@Transactional
	public void revokeAll(Long userId) {
		sessions.revokeAll(userId, Instant.now());
	}

	@Override
	@Transactional
	public void revokeAllExcept(Long userId, Long keepSessionId) {
		sessions.revokeAllExcept(userId, keepSessionId, Instant.now());
	}

	private TokenResponse tokens(User user, UserSession session, String refreshToken, Instant now) {
		return new TokenResponse(tokenService.issue(user, session.getId()), "Bearer",
				jwtProperties.accessTokenTtl().toSeconds(), refreshToken,
				Duration.between(now, session.getExpiresAt()).toSeconds());
	}

	private static BusinessException invalidRefreshToken() {
		return new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
	}

}
