package com.company.project.auth.services;

import com.company.project.auth.dto.ChangePasswordRequest;
import com.company.project.auth.dto.ResetPasswordRequest;

/** Forgotten and changed passwords. Both end other sign-ins: a new password is often a reaction to a leak. */
public interface PasswordService {

	/**
	 * Sends a reset link when the email belongs to an enabled account. Does nothing otherwise, so the caller cannot
	 * tell which emails are registered.
	 */
	void forgotPassword(String email);

	/**
	 * Sets the new password, marks the email verified (the link proves the user reads it), lifts any lockout and
	 * signs out every device. BusinessException 400 for an unknown, used or expired link.
	 */
	void resetPassword(ResetPasswordRequest request);

	/**
	 * Sets the new password after checking the current one (BusinessException 400 when wrong) and signs out every
	 * other device; {@code currentSessionId} stays signed in.
	 */
	void changePassword(Long userId, Long currentSessionId, ChangePasswordRequest request);

}
