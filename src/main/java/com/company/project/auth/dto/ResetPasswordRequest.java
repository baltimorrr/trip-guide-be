package com.company.project.auth.dto;

import com.company.project.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param token the {@code token} query parameter of the emailed reset link */
public record ResetPasswordRequest(@NotBlank @Size(max = 100) String token, @NotBlank @StrongPassword String newPassword) {

	@Override
	public String toString() {
		return "ResetPasswordRequest[token=***, newPassword=***]";
	}

}
