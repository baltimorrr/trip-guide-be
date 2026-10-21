package com.company.project.auth.services;

import com.company.project.auth.dto.LoginRequest;
import com.company.project.auth.dto.RegisterRequest;
import com.company.project.auth.dto.TokenResponse;
import com.company.project.user.dto.UserResponse;
import org.springframework.security.oauth2.jwt.Jwt;

/** Self-registration, password login, and the current user. */
public interface AuthService {

	/** Creates an account with role USER. */
	UserResponse register(RegisterRequest request);

	/** Throws an AuthenticationException (→ 401, same message for every failure) on bad credentials. */
	TokenResponse login(LoginRequest request);

	/** The user the token was issued to; 404 if that account no longer exists. */
	UserResponse currentUser(Jwt jwt);

}
