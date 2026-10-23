package com.company.project.auth.services;

import com.company.project.auth.dto.ClientInfo;
import com.company.project.auth.dto.LoginRequest;
import com.company.project.auth.dto.RegisterRequest;
import com.company.project.auth.dto.TokenResponse;
import com.company.project.user.dto.UserResponse;

/** Self-registration, email verification, and password sign-in and sign-out. */
public interface AuthService {

	/** Creates an unverified account with role USER and sends its verification link. */
	UserResponse register(RegisterRequest request);

	/** Marks the email of the link's account verified. BusinessException 400 for an unknown, used or expired link. */
	void verifyEmail(String token);

	/**
	 * Sends a fresh verification link when the email belongs to an enabled, unverified account. Does nothing
	 * otherwise, so the caller cannot tell which emails are registered.
	 */
	void resendVerification(String email);

	/**
	 * Opens a session for this device. Throws an AuthenticationException (→ 401, same message for every failure) on
	 * bad credentials or a locked account, and BusinessException 403 when the password is right but the email is not
	 * verified yet. Repeated failures lock the account ({@code app.auth.lockout}).
	 */
	TokenResponse login(LoginRequest request, ClientInfo client);

	/** See {@link SessionService#refresh}. */
	TokenResponse refresh(String refreshToken);

	/** Signs out the device holding this refresh token. */
	void logout(String refreshToken);

	/** Signs the user out everywhere, including the device making the request. */
	void logoutAll(Long userId);

}
