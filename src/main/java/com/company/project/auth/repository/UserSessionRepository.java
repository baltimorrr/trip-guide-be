package com.company.project.auth.repository;

import java.time.Instant;

import com.company.project.auth.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

/** Signed-in devices. Hand-written queries are in {@link UserSessionRepositoryCustom}. */
public interface UserSessionRepository extends JpaRepository<UserSession, Long>, UserSessionRepositoryCustom {

	/** Checked on every authenticated request: the access token's session is live and its user still enabled. */
	boolean existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue(Long id, Instant now);

}
