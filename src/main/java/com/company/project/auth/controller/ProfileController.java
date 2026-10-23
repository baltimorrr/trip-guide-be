package com.company.project.auth.controller;

import com.company.project.auth.dto.ChangePasswordRequest;
import com.company.project.auth.security.SessionTokenValidator;
import com.company.project.auth.services.PasswordService;
import com.company.project.user.dto.ProfileUpdateRequest;
import com.company.project.user.dto.UserResponse;
import com.company.project.user.services.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's profile and password. The user is the token's subject and is reloaded on every call, so a
 * deleted account is never served from a token.
 */
@RestController
@RequestMapping("/api/auth/me")
@RequiredArgsConstructor
public class ProfileController {

	private final ProfileService profileService;

	private final PasswordService passwordService;

	@GetMapping
	public UserResponse get(@AuthenticationPrincipal Jwt jwt) {
		return profileService.get(userId(jwt));
	}

	@PutMapping
	public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
		return profileService.update(userId(jwt), request);
	}

	/** Signs out every other device; this one stays signed in. */
	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
		// The decoder only accepts tokens with a live session, so the id is always there.
		passwordService.changePassword(userId(jwt), SessionTokenValidator.sessionId(jwt), request);
	}

	private static Long userId(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}

}
