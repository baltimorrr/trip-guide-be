package com.company.project.auth.repository.impl;

import java.time.Instant;
import java.util.Optional;

import com.company.project.auth.model.TokenType;
import com.company.project.auth.model.UserToken;
import com.company.project.auth.repository.UserTokenRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/** One-time link queries, all JPQL. Bulk updates bump {@code version} and set {@code updatedAt} themselves. */
@RequiredArgsConstructor
public class UserTokenRepositoryImpl implements UserTokenRepositoryCustom {

	private static final String BY_HASH = "select t from UserToken t where t.tokenHash = :hash";

	private static final String INVALIDATE_UNUSED = """
			update versioned UserToken t set t.usedAt = :now, t.updatedAt = :now
			where t.user.id = :userId and t.type = :type and t.usedAt is null""";

	private final EntityManager entityManager;

	@Override
	public Optional<UserToken> findByTokenHashForUpdate(String tokenHash) {
		return entityManager.createQuery(BY_HASH, UserToken.class)
			.setParameter("hash", tokenHash)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.getResultStream()
			.findFirst();
	}

	@Override
	@Transactional
	public int invalidateUnused(Long userId, TokenType type, Instant now) {
		return entityManager.createQuery(INVALIDATE_UNUSED)
			.setParameter("userId", userId)
			.setParameter("type", type)
			.setParameter("now", now)
			.executeUpdate();
	}

}
