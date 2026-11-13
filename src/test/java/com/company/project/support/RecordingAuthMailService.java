package com.company.project.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.company.project.auth.services.AuthMailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Keeps the last emailed link per address so tests can follow it, the way a user clicks the link in the email.
 * Every {@link IntegrationTest} gets it through {@link Config}; the map is static because {@link TestAuth} is a
 * static helper. Tests use unique emails, so they never read each other's links.
 */
public class RecordingAuthMailService implements AuthMailService {

	private static final Map<String, String> VERIFICATION_LINKS = new ConcurrentHashMap<>();

	private static final Map<String, String> RESET_LINKS = new ConcurrentHashMap<>();

	@Override
	public void sendEmailVerification(String email, String link) {
		VERIFICATION_LINKS.put(email, link);
	}

	@Override
	public void sendPasswordReset(String email, String link) {
		RESET_LINKS.put(email, link);
	}

	/** The last verification link sent to this (normalized) email, or null. */
	public static String verificationLink(String email) {
		return VERIFICATION_LINKS.get(email);
	}

	/** The last password reset link sent to this (normalized) email, or null. */
	public static String resetLink(String email) {
		return RESET_LINKS.get(email);
	}

	public static String verificationToken(String email) {
		return token(verificationLink(email), "verification", email);
	}

	public static String resetToken(String email) {
		return token(resetLink(email), "password reset", email);
	}

	private static String token(String link, String kind, String email) {
		if (link == null) {
			throw new AssertionError("No %s link was sent to %s".formatted(kind, email));
		}
		return UriComponentsBuilder.fromUriString(link).build().getQueryParams().getFirst("token");
	}

	/** Replaces the logging mail service in the shared test context. */
	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		RecordingAuthMailService recordingAuthMailService() {
			return new RecordingAuthMailService();
		}

	}

}
