package com.company.project.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

	// Records print every field by default; keep the password out of logs.
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***]";
	}

}
