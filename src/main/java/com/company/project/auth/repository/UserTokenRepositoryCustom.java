package com.company.project.auth.repository;

import java.time.Instant;
import java.util.Optional;

import com.company.project.auth.model.TokenType;
import com.company.project.auth.model.UserToken;

/** Hand-written one-time link queries, implemented in {@code impl/UserTokenRepositoryImpl}. */
public interface UserTokenRepositoryCustom {

	/** The link with this hash, row-locked so two requests cannot both use it. */
	Optional<UserToken> findByTokenHashForUpdate(String tokenHash);

	/** Marks the user's unused links of this type as used, so only the newest link works. */
	int invalidateUnused(Long userId, TokenType type, Instant now);

}
