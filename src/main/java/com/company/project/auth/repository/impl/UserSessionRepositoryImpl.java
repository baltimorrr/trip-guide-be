package com.company.project.auth.repository.impl;

import java.time.Instant;
import java.util.Optional;

import com.company.project.auth.model.UserSession;
import com.company.project.auth.repository.UserSessionRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/** Session queries, all JPQL. Bulk updates bump {@code version} and set {@code updatedAt} themselves. */
@RequiredArgsConstructor
public class UserSessionRepositoryImpl implements UserSessionRepositoryCustom {

	private static final String BY_CURRENT_OR_PREVIOUS_HASH = """
			select s from UserSession s
			where s.refreshTokenHash = :hash or s.previousTokenHash = :hash""";

	private static final String ACTIVE_BY_HASH = """
			select s from UserSession s
			where s.refreshTokenHash = :hash and s.revokedAt is null and s.expiresAt > :now""";

	private static final String REVOKE_ALL = """
			update versioned UserSession s set s.revokedAt = :now, s.updatedAt = :now
			where s.user.id = :userId and s.revokedAt is null""";

	private static final String REVOKE_ALL_EXCEPT = REVOKE_ALL + " and s.id <> :keepId";

	private final EntityManager entityManager;

	@Override
	public Optional<UserSession> findByTokenHashForUpdate(String tokenHash) {
		return entityManager.createQuery(BY_CURRENT_OR_PREVIOUS_HASH, UserSession.class)
			.setParameter("hash", tokenHash)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.getResultStream()
			.findFirst();
	}

	@Override
	public Optional<UserSession> findActiveByTokenHash(String tokenHash, Instant now) {
		return entityManager.createQuery(ACTIVE_BY_HASH, UserSession.class)
			.setParameter("hash", tokenHash)
			.setParameter("now", now)
			.getResultStream()
			.findFirst();
	}

	@Override
	@Transactional
	public int revokeAll(Long userId, Instant now) {
		return entityManager.createQuery(REVOKE_ALL)
			.setParameter("userId", userId)
			.setParameter("now", now)
			.executeUpdate();
	}

	@Override
	@Transactional
	public int revokeAllExcept(Long userId, Long keepSessionId, Instant now) {
		return entityManager.createQuery(REVOKE_ALL_EXCEPT)
			.setParameter("userId", userId)
			.setParameter("keepId", keepSessionId)
			.setParameter("now", now)
			.executeUpdate();
	}

}
