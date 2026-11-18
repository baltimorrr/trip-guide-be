package com.company.project.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param token the {@code token} query parameter of the emailed link */
public record VerifyEmailRequest(
		@Schema(example = "token-from-the-verification-link-in-the-app-log") @NotBlank @Size(max = 100) String token) {

	@Override
	public String toString() {
		return "VerifyEmailRequest[token=***]";
	}

}
