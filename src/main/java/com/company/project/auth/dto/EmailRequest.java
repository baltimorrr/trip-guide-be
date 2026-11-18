package com.company.project.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An email address to send an account link to (resend verification, forgot password). */
public record EmailRequest(@Schema(example = "demo@example.com") @NotBlank @Email @Size(max = 254) String email) {
}
