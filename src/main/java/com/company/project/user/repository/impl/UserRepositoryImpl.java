package com.company.project.user.repository.impl;

import java.time.Instant;

import com.company.project.user.repository.UserRepositoryCustom;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lockout counters, as single JPQL updates so parallel failed sign-ins never lose a count. Bulk updates bypass
 * auditing and entity versioning, so they bump {@code version} ({@code update versioned}) and set
 * {@code updatedAt} themselves.
 */
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

	/**
	 * Every right-hand side reads the row as it was before the update, so the lock condition repeats the "same
	 * window" test instead of reading the new count. The first failure of a window never locks (maxAttempts >= 2).
	 */
	private static final String RECORD_FAILED_LOGIN = """
			update versioned User u set
			  u.failedLoginCount = case when u.firstFailedLoginAt is null or u.firstFailedLoginAt < :windowStart
			                            then 1 else u.failedLoginCount + 1 end,
			  u.firstFailedLoginAt = case when u.firstFailedLoginAt is null or u.firstFailedLoginAt < :windowStart
			                              then :now else u.firstFailedLoginAt end,
			  u.lockedUntil = case when u.firstFailedLoginAt >= :windowStart and u.failedLoginCount + 1 >= :maxAttempts
			                       then :lockUntil else u.lockedUntil end,
			  u.updatedAt = :now
			where u.email = :email""";

	private static final String CLEAR_FAILED_LOGINS = """
			update versioned User u set
			  u.failedLoginCount = 0, u.firstFailedLoginAt = null, u.lockedUntil = null, u.updatedAt = :now
			where u.id = :id""";

	private final EntityManager entityManager;

	@Override
	@Transactional
	public void recordFailedLogin(String email, Instant now, Instant windowStart, int maxAttempts, Instant lockUntil) {
		entityManager.createQuery(RECORD_FAILED_LOGIN)
			.setParameter("email", email)
			.setParameter("now", now)
			.setParameter("windowStart", windowStart)
			.setParameter("maxAttempts", maxAttempts)
			.setParameter("lockUntil", lockUntil)
			.executeUpdate();
	}

	@Override
	@Transactional
	public void clearFailedLogins(Long userId, Instant now) {
		entityManager.createQuery(CLEAR_FAILED_LOGINS).setParameter("id", userId).setParameter("now", now).executeUpdate();
	}

}
