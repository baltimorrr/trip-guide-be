package com.company.project.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
		@Schema(example = "refreshToken-from-the-login-response") @NotBlank @Size(max = 100) String refreshToken) {

	@Override
	public String toString() {
		return "RefreshTokenRequest[refreshToken=***]";
	}

}
