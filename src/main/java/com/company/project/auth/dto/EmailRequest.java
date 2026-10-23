package com.company.project.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An email address to send an account link to (resend verification, forgot password). */
public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
}
