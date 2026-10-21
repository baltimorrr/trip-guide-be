package com.company.project.auth.dto;

/** @param expiresIn token lifetime in seconds */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
