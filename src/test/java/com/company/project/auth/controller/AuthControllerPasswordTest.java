package com.company.project.auth.controller;

import static com.company.project.support.TestAuth.USER_PASSWORD;
import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.login;
import static com.company.project.support.TestAuth.register;
import static com.company.project.support.TestAuth.registerVerified;
import static com.company.project.support.TestAuth.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import com.company.project.auth.security.OpaqueTokens;
import com.company.project.support.IntegrationTest;
import com.company.project.support.RecordingAuthMailer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Forgot password and reset by emailed link. */
@IntegrationTest
class AuthControllerPasswordTest {

	static final String NEW_PASSWORD = "brand-new-password-2";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void forgotPasswordAnswersTheSameForUnknownEmails() {
		String known = verifiedUser();
		String unknown = uniqueEmail();

		assertThat(forgot(known.toUpperCase(Locale.ROOT))).hasStatus(HttpStatus.ACCEPTED);
		assertThat(forgot(unknown)).hasStatus(HttpStatus.ACCEPTED);

		assertThat(RecordingAuthMailer.resetLink(known)).startsWith("http://localhost:5173/reset-password?token=");
		assertThat(RecordingAuthMailer.resetLink(unknown)).isNull();
	}

	@Test
	void resetSetsTheNewPasswordAndSignsOutEveryDevice() {
		String email = verifiedUser();
		MvcTestResult before = login(mvc, email, USER_PASSWORD);
		String accessBefore = JsonPath.read(body(before), "$.accessToken");
		String refreshBefore = JsonPath.read(body(before), "$.refreshToken");
		forgot(email);

		assertThat(reset(RecordingAuthMailer.resetToken(email), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(login(mvc, email, USER_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login(mvc, email, NEW_PASSWORD)).hasStatusOk();
		assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(accessBefore)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"refreshToken": "%s"}""".formatted(refreshBefore)))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void resetLinkWorksOnce() {
		String email = verifiedUser();
		forgot(email);
		String token = RecordingAuthMailer.resetToken(email);
		assertThat(reset(token, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult again = reset(token, "another-password-3");

		assertThat(again).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(again).bodyJson().extractingPath("$.detail").isEqualTo("Invalid or expired link");
		assertThat(login(mvc, email, NEW_PASSWORD)).hasStatusOk();
	}

	@Test
	void expiredOrReplacedResetLinkIsRejected() {
		String email = verifiedUser();
		forgot(email);
		String first = RecordingAuthMailer.resetToken(email);
		forgot(email);
		String second = RecordingAuthMailer.resetToken(email);
		jdbc.sql("update user_tokens set expires_at = now() - interval '1 second' where token_hash = :hash")
			.param("hash", OpaqueTokens.hash(second))
			.update();

		assertThat(reset(first, NEW_PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(reset(second, NEW_PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(reset(OpaqueTokens.generate(), NEW_PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(login(mvc, email, USER_PASSWORD)).hasStatusOk();
	}

	@Test
	void weakNewPasswordIsRejectedAndTheLinkStillWorks() {
		String email = verifiedUser();
		forgot(email);
		String token = RecordingAuthMailer.resetToken(email);

		MvcTestResult weak = reset(token, "password");

		assertThat(weak).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(weak).bodyJson().extractingPath("$.errors[*].field").asArray().containsOnly("newPassword");
		assertThat(reset(token, NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void resetUnlocksALockedAccount() {
		String email = verifiedUser();
		for (int attempt = 1; attempt <= 5; attempt++) {
			login(mvc, email, "wrong-password-1");
		}
		forgot(email);

		assertThat(reset(RecordingAuthMailer.resetToken(email), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(login(mvc, email, NEW_PASSWORD)).hasStatusOk();
	}

	@Test
	void resetVerifiesAnAccountThatNeverUsedItsVerificationLink() {
		String email = uniqueEmail();
		register(mvc, email, USER_PASSWORD);
		forgot(email);

		assertThat(reset(RecordingAuthMailer.resetToken(email), NEW_PASSWORD)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(login(mvc, email, NEW_PASSWORD)).hasStatusOk();
	}

	private String verifiedUser() {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);
		return email;
	}

	private MvcTestResult forgot(String email) {
		return mvc.post().uri("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s"}""".formatted(email))
			.exchange();
	}

	private MvcTestResult reset(String token, String newPassword) {
		return mvc.post().uri("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token": "%s", "newPassword": "%s"}""".formatted(token, newPassword))
			.exchange();
	}

}
