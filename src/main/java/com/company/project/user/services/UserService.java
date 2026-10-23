package com.company.project.user.services;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.company.project.user.model.Role;
import com.company.project.user.model.User;

/** Creates user accounts and sets passwords. Shared by registration, the admin bootstrap and password changes. */
public interface UserService {

	/**
	 * BCrypt only uses the first 72 bytes of a password and Spring Security rejects longer
	 * input. Bean Validation's {@code @Size} counts characters, and one character can take up
	 * to 4 bytes in UTF-8, so the byte length is checked separately.
	 */
	int MAX_PASSWORD_BYTES = 72;

	/**
	 * Throws BusinessException: 400 when the password is over {@link #MAX_PASSWORD_BYTES}, 409 when the email is taken.
	 *
	 * @param phone optional
	 * @param emailVerified false for self-registration: the account cannot sign in until its email is verified
	 */
	User create(String email, String rawPassword, String fullName, String phone, Set<Role> roles, boolean emailVerified);

	/**
	 * Sets a new password (and lifts any sign-in lockout) on a user loaded in the caller's transaction. Throws
	 * BusinessException 400 when the password is over {@link #MAX_PASSWORD_BYTES}.
	 */
	void changePassword(User user, String rawPassword);

	static boolean exceedsPasswordByteLimit(String rawPassword) {
		return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
	}

}
