package com.company.project.auth.dto;

import com.company.project.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** The Swagger examples keep the demo account's password, so its login example keeps working. */
public record ChangePasswordRequest(
		@Schema(example = "demo-password-1") @NotBlank String currentPassword,
		@Schema(example = "demo-password-1") @NotBlank @StrongPassword String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
	}

}
