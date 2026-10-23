package com.company.project.auth.model;

import java.time.Instant;

import com.company.project.common.model.BaseEntity;
import com.company.project.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One signed-in device. The refresh token rotates on every refresh; the hash it replaced is kept so a replayed old
 * token is recognised as reuse. Access tokens carry this session's id and stop working once it is revoked.
 */
@Entity
@Table(name = "user_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession extends BaseEntity {

	static final int MAX_USER_AGENT_LENGTH = 500;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@Column(name = "refresh_token_hash", nullable = false, length = 64)
	private String refreshTokenHash;

	@Column(name = "previous_token_hash", length = 64)
	private String previousTokenHash;

	@Column(name = "user_agent", length = MAX_USER_AGENT_LENGTH, updatable = false)
	private String userAgent;

	@Column(length = 45, updatable = false)
	private String ip;

	/** Fixed at sign-in: refreshing never extends a session. */
	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	public static UserSession create(User user, String refreshTokenHash, Instant expiresAt, String userAgent, String ip) {
		UserSession session = new UserSession();
		session.user = user;
		session.refreshTokenHash = refreshTokenHash;
		session.expiresAt = expiresAt;
		session.userAgent = userAgent == null || userAgent.length() <= MAX_USER_AGENT_LENGTH ? userAgent
				: userAgent.substring(0, MAX_USER_AGENT_LENGTH);
		session.ip = ip;
		return session;
	}

	public boolean isActive(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	/** Replaces the refresh token; the old hash is kept to recognise a replay. */
	public void rotate(String newRefreshTokenHash, Instant now) {
		previousTokenHash = refreshTokenHash;
		refreshTokenHash = newRefreshTokenHash;
		lastUsedAt = now;
	}

	/** Keeps the first revocation time when called again. */
	public void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

}
