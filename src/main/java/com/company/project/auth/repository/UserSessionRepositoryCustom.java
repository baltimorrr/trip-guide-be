package com.company.project.auth.repository;

import java.time.Instant;
import java.util.Optional;

import com.company.project.auth.model.UserSession;

/** Hand-written session queries, implemented in {@code impl/UserSessionRepositoryImpl}. */
public interface UserSessionRepositoryCustom {

	/**
	 * The session whose current or previous refresh token has this hash, row-locked so concurrent refreshes of one
	 * session run one after the other.
	 */
	Optional<UserSession> findByTokenHashForUpdate(String tokenHash);

	/** The live session whose current refresh token has this hash. */
	Optional<UserSession> findActiveByTokenHash(String tokenHash, Instant now);

	/** Revokes every live session of the user; returns how many. */
	int revokeAll(Long userId, Instant now);

	/** Revokes every live session of the user except {@code keepSessionId}; returns how many. */
	int revokeAllExcept(Long userId, Long keepSessionId, Instant now);

}
