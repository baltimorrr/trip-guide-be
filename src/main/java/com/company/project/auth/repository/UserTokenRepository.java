package com.company.project.auth.repository;

import java.time.Instant;

import com.company.project.auth.model.TokenType;
import com.company.project.auth.model.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

/** One-time emailed links. Hand-written queries are in {@link UserTokenRepositoryCustom}. */
public interface UserTokenRepository extends JpaRepository<UserToken, Long>, UserTokenRepositoryCustom {

	/** Whether a link of this type was sent to the user after {@code since} (resend cooldown). */
	boolean existsByUserIdAndTypeAndCreatedAtAfter(Long userId, TokenType type, Instant since);

}
