package com.company.project.auth.services;

import java.util.Optional;

import com.company.project.auth.model.TokenType;
import com.company.project.user.model.User;

/** One-time tokens for emailed links. Only their hashes are stored. */
public interface UserTokenService {

	/**
	 * A new token of this type for the user; any older unused one stops working. Empty when a link of this type was
	 * sent to the user less than {@code app.auth.email-cooldown} ago.
	 */
	Optional<String> issue(User user, TokenType type);

	/**
	 * Uses up the token and returns its user, a lazy reference: call within the caller's transaction. Throws
	 * BusinessException 400 when the token is unknown, of another type, already used or expired.
	 */
	User consume(String token, TokenType type);

}
