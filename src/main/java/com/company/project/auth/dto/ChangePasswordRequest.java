package com.company.project.auth.dto;

import com.company.project.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @StrongPassword String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
	}

}
