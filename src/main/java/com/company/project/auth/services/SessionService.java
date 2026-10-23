package com.company.project.auth.services;

import com.company.project.auth.dto.ClientInfo;
import com.company.project.auth.dto.TokenResponse;
import com.company.project.user.model.User;

/**
 * Signed-in devices (refresh-token sessions). A session is revoked by sign-out, by a replayed refresh token, or by
 * a password change; its access tokens stop working at once because each request checks the session.
 */
public interface SessionService {

	/** Opens a session for a user who just proved their password, and returns its first token pair. */
	TokenResponse open(User user, boolean rememberMe, ClientInfo client);

	/**
	 * Exchanges a refresh token for a new pair; the old refresh token stops working. Presenting a refresh token that
	 * was already exchanged revokes its session (the token was probably stolen). Throws BusinessException 401 with
	 * one message for every failure.
	 */
	TokenResponse refresh(String refreshToken);

	/** Revokes the session of this refresh token; does nothing for an unknown or dead token. */
	void revoke(String refreshToken);

	void revokeAll(Long userId);

	void revokeAllExcept(Long userId, Long keepSessionId);

}
