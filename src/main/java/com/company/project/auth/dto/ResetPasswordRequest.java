package com.company.project.auth.dto;

import com.company.project.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The Swagger example resets the demo account to its own password, so its login example keeps working.
 *
 * @param token the {@code token} query parameter of the emailed reset link
 */
public record ResetPasswordRequest(
		@Schema(example = "token-from-the-reset-link-in-the-app-log") @NotBlank @Size(max = 100) String token,
		@Schema(example = "demo-password-1") @NotBlank @StrongPassword String newPassword) {

	@Override
	public String toString() {
		return "ResetPasswordRequest[token=***, newPassword=***]";
	}

}
