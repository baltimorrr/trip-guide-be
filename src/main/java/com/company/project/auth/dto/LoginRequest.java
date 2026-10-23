package com.company.project.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** @param rememberMe keeps the sign-in for {@code app.auth.remember-me-ttl} instead of the shorter default */
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, Boolean rememberMe) {

	public boolean isRememberMe() {
		return Boolean.TRUE.equals(rememberMe);
	}

	// Records print every field by default; keep the password out of logs.
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***, rememberMe=" + rememberMe + "]";
	}

}
