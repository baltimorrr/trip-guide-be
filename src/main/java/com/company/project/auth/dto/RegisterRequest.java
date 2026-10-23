package com.company.project.auth.dto;

import com.company.project.common.validation.PhoneNumber;
import com.company.project.common.validation.StrongPassword;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @StrongPassword String password,
		@NotBlank String confirmPassword,
		@NotBlank @Size(max = 100) String fullName,
		@PhoneNumber String phone) {

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
