package com.company.project.user.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An application user. Deliberately no Lombok {@code @Data}/{@code @ToString}: a generated
 * toString would print the password hash, and generated equals/hashCode break JPA identity.
 * State changes go through the domain methods below, which hold the field rules.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(length = 30)
	private String phone;

	/** Nationality, ISO 3166 alpha-2. */
	@Column(name = "country_code", length = 2)
	private String countryCode;

	/** ISO 4217 code. */
	@Column(name = "preferred_currency", length = 3)
	private String preferredCurrency;

	@Column(nullable = false)
	private boolean enabled = true;

	/** Null until the link from the verification email is used; an unverified account cannot sign in. */
	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	// Lockout state. Written by UserRepository's bulk updates on sign-in, cleared on a successful sign-in or a
	// password change.
	@Column(name = "failed_login_count", nullable = false)
	private short failedLoginCount;

	@Column(name = "first_failed_login_at")
	private Instant firstFailedLoginAt;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	// Roles are always needed to build the security principal, so load them with the user.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Set<Role> roles = new HashSet<>();

	/** @param emailVerifiedAt null for a self-registered account that still has to verify its email */
	public static User create(String email, String passwordHash, String fullName, String phone, Set<Role> roles,
			Instant emailVerifiedAt) {
		User user = new User();
		user.email = normalizeEmail(email);
		user.passwordHash = passwordHash;
		user.fullName = fullName.trim();
		user.phone = trimToNull(phone);
		user.roles = new HashSet<>(roles);
		user.emailVerifiedAt = emailVerifiedAt;
		return user;
	}

	/** Emails are compared case-insensitively by storing them trimmed and lowercased. */
	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	/** Keeps the first verification time when called again. */
	public void verifyEmail(Instant now) {
		if (emailVerifiedAt == null) {
			emailVerifiedAt = now;
		}
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	/** A new password also lifts any sign-in lockout. */
	public void changePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
		this.failedLoginCount = 0;
		this.firstFailedLoginAt = null;
		this.lockedUntil = null;
	}

	/** Replaces every profile field; blank optional values are cleared and codes are stored uppercase. */
	public void updateProfile(String fullName, String phone, String countryCode, String preferredCurrency) {
		this.fullName = fullName.trim();
		this.phone = trimToNull(phone);
		this.countryCode = upperOrNull(countryCode);
		this.preferredCurrency = upperOrNull(preferredCurrency);
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String upperOrNull(String value) {
		String trimmed = trimToNull(value);
		return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
	}

}
