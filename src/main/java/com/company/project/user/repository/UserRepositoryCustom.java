package com.company.project.user.repository;

import java.time.Instant;

/** Sign-in lockout bookkeeping, implemented in {@code impl/UserRepositoryImpl}. */
public interface UserRepositoryCustom {

	/**
	 * Counts one failed sign-in for the account with this (normalized) email; does nothing for an unknown email.
	 * A failure after {@code windowStart} adds to the current window, an older window restarts at 1. The
	 * {@code maxAttempts}-th failure inside one window locks the account until {@code lockUntil}.
	 */
	void recordFailedLogin(String email, Instant now, Instant windowStart, int maxAttempts, Instant lockUntil);

	/** Forgets failed sign-ins and any lock, after a successful sign-in. */
	void clearFailedLogins(Long userId, Instant now);

}
