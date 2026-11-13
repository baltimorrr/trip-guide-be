package com.company.project.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Register/verify/login helpers for API tests. Each test uses fresh emails so tests never share users. Emailed
 * links are read from {@link RecordingAuthMailService}.
 */
public final class TestAuth {

	/** Must match app.bootstrap.admin in src/test/resources/application-test.yml. */
	public static final String ADMIN_EMAIL = "admin@test.local";

	public static final String ADMIN_PASSWORD = "admin-test-password-123";

	public static final String USER_PASSWORD = "user-password-123";

	private TestAuth() {
	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.local";
	}

	/** Registers an account that still has to verify its email. */
	public static MvcTestResult register(MockMvcTester mvc, String email, String password) {
		String json = """
				{"email": "%s", "password": "%s", "confirmPassword": "%s", "fullName": "Test User"}"""
			.formatted(email, password, password);
		return mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	/** Uses the link from the verification email, as the user would. */
	public static MvcTestResult verifyEmail(MockMvcTester mvc, String token) {
		String json = """
				{"token": "%s"}""".formatted(token);
		return mvc.post().uri("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	/** Registers an account and verifies its email, so it can sign in. */
	public static void registerVerified(MockMvcTester mvc, String email, String password) {
		assertThat(register(mvc, email, password)).hasStatus(HttpStatus.CREATED);
		assertThat(verifyEmail(mvc, RecordingAuthMailService.verificationToken(email))).hasStatus(HttpStatus.NO_CONTENT);
	}

	public static MvcTestResult login(MockMvcTester mvc, String email, String password) {
		String json = """
				{"email": "%s", "password": "%s"}""".formatted(email, password);
		return mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	public static String accessToken(MockMvcTester mvc, String email, String password) {
		MvcTestResult result = login(mvc, email, password);
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.accessToken");
	}

	/** Registers and verifies a new USER and returns its access token. */
	public static String userToken(MockMvcTester mvc) {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);
		return accessToken(mvc, email, USER_PASSWORD);
	}

	/** Access token of the bootstrap admin (roles ADMIN + USER). */
	public static String adminToken(MockMvcTester mvc) {
		return accessToken(mvc, ADMIN_EMAIL, ADMIN_PASSWORD);
	}

	public static String bearer(String token) {
		return "Bearer " + token;
	}

	public static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
