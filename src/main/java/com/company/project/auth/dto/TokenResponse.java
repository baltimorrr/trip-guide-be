package com.company.project.auth.dto;

/**
 * A signed-in session: a short-lived access token for API calls and the refresh token that replaces it.
 *
 * @param expiresIn access token lifetime in seconds
 * @param refreshToken send to {@code /api/auth/refresh} for a new pair; it works once
 * @param refreshExpiresIn seconds until the session ends and the user must sign in again
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
		long refreshExpiresIn) {

	@Override
	public String toString() {
		return "TokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn
				+ ", refreshToken=***, refreshExpiresIn=" + refreshExpiresIn + "]";
	}

}
