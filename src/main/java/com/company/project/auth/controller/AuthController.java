package com.company.project.auth.controller;

import com.company.project.auth.dto.ClientInfo;
import com.company.project.auth.dto.EmailRequest;
import com.company.project.auth.dto.LoginRequest;
import com.company.project.auth.dto.RefreshTokenRequest;
import com.company.project.auth.dto.RegisterRequest;
import com.company.project.auth.dto.ResetPasswordRequest;
import com.company.project.auth.dto.TokenResponse;
import com.company.project.auth.dto.VerifyEmailRequest;
import com.company.project.auth.services.AuthService;
import com.company.project.auth.services.PasswordService;
import com.company.project.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign-up, sign-in and sign-out, and recovering a forgotten password. The signed-in user's own data is in ProfileController. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final PasswordService passwordService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@SecurityRequirements // public: no bearer token in the API docs
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	/** The token from the link in the verification email. */
	@PostMapping("/verify-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirements // public: no bearer token in the API docs
	public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		authService.verifyEmail(request.token());
	}

	/** Always 202, whether or not the email belongs to an unverified account. */
	@PostMapping("/verify-email/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@SecurityRequirements // public: no bearer token in the API docs
	public void resendVerification(@Valid @RequestBody EmailRequest request) {
		authService.resendVerification(request.email());
	}

	/** Paste the {@code accessToken} of the response into Swagger UI's Authorize to call the other endpoints. */
	@PostMapping("/login")
	@SecurityRequirements // public: no bearer token in the API docs
	// Swagger UI's example dropdown: the demo accounts of the local seed (db/seed/R__demo_catalog.sql).
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
			@ExampleObject(name = "Demo customer", value = """
					{"email": "demo@example.com", "password": "demo-password-1", "rememberMe": false}"""),
			@ExampleObject(name = "Demo admin", value = """
					{"email": "demo-admin@example.com", "password": "demo-admin-password-1", "rememberMe": false}""") }))
	public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return authService.login(request, ClientInfo.from(httpRequest));
	}

	/** A new token pair; the refresh token sent here stops working. Send one refresh at a time per device. */
	@PostMapping("/refresh")
	@SecurityRequirements // public: no bearer token in the API docs
	public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return authService.refresh(request.refreshToken());
	}

	/** Signs out this device. Always 204. */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirements // public: no bearer token in the API docs
	public void logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
	}

	/** Signs out every device of the current user, this one included. */
	@PostMapping("/logout-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logoutAll(@AuthenticationPrincipal Jwt jwt) {
		authService.logoutAll(Long.valueOf(jwt.getSubject()));
	}

	/** Always 202, whether or not the email belongs to an account. */
	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@SecurityRequirements // public: no bearer token in the API docs
	public void forgotPassword(@Valid @RequestBody EmailRequest request) {
		passwordService.forgotPassword(request.email());
	}

	/** The token from the link in the reset email. Signs out every device. */
	@PostMapping("/password/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirements // public: no bearer token in the API docs
	public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordService.resetPassword(request);
	}

}
