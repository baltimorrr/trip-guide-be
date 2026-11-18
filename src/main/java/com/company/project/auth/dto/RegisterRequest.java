package com.company.project.auth.dto;

import com.company.project.common.validation.PhoneNumber;
import com.company.project.common.validation.StrongPassword;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@Schema(example = "new-user@example.com") @NotBlank @Email @Size(max = 254) String email,
		@Schema(example = "new-user-password-1") @NotBlank @StrongPassword String password,
		@Schema(example = "new-user-password-1") @NotBlank String confirmPassword,
		@Schema(example = "New User") @NotBlank @Size(max = 100) String fullName,
		@Schema(example = "+84907654321") @PhoneNumber String phone) {

	/** Reported as the field {@code passwordConfirmed}. A missing password is left to {@code @NotBlank}. */
	@JsonIgnore
	@AssertTrue(message = "must match password")
	public boolean isPasswordConfirmed() {
		return password == null || password.equals(confirmPassword);
	}

	// Records print every field by default; keep the passwords out of logs.
	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + ", password=***, confirmPassword=***, fullName=" + fullName
				+ ", phone=" + phone + "]";
	}

}
