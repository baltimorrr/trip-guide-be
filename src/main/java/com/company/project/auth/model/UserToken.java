package com.company.project.auth.model;

import java.time.Instant;

import com.company.project.common.model.BaseEntity;
import com.company.project.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A one-time link sent by email (verify email, reset password). Only the SHA-256 of the token is stored. */
@Entity
@Table(name = "user_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserToken extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private TokenType type;

	@Column(name = "token_hash", nullable = false, length = 64, updatable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	public static UserToken create(User user, TokenType type, String tokenHash, Instant expiresAt) {
		UserToken token = new UserToken();
		token.user = user;
		token.type = type;
		token.tokenHash = tokenHash;
		token.expiresAt = expiresAt;
		return token;
	}

	/** Not used yet and not expired. */
	public boolean isUsable(Instant now) {
		return usedAt == null && expiresAt.isAfter(now);
	}

	public void markUsed(Instant now) {
		usedAt = now;
	}

}
