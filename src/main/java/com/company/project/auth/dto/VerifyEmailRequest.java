package com.company.project.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param token the {@code token} query parameter of the emailed link */
public record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {

	@Override
	public String toString() {
		return "VerifyEmailRequest[token=***]";
	}

}
