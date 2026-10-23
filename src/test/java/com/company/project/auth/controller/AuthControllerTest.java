package com.company.project.auth.controller;

import static com.company.project.support.TestAuth.USER_PASSWORD;
import static com.company.project.support.TestAuth.adminToken;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.login;
import static com.company.project.support.TestAuth.register;
import static com.company.project.support.TestAuth.registerVerified;
import static com.company.project.support.TestAuth.uniqueEmail;
import static com.company.project.support.TestAuth.verifyEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import com.company.project.auth.security.OpaqueTokens;
import com.company.project.support.IntegrationTest;
import com.company.project.support.RecordingAuthMailer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AuthControllerTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	JdbcClient jdbc;

	@Test
	void registerCreatesUserWithoutExposingPassword() {
		String email = uniqueEmail();

		MvcTestResult result = register(mvc, email, USER_PASSWORD);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(email);
		assertThat(result).bodyJson().extractingPath("$.roles").asArray().containsExactly("USER");
		assertThat(result).bodyJson().doesNotHavePath("$.password");
		assertThat(result).bodyJson().doesNotHavePath("$.passwordHash");
	}

	@Test
	void duplicateEmailIsRejectedCaseInsensitively() {
		String email = uniqueEmail();
		assertThat(register(mvc, email, USER_PASSWORD)).hasStatus(HttpStatus.CREATED);

		MvcTestResult duplicate = register(mvc, email.toUpperCase(Locale.ROOT), USER_PASSWORD);

		assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
		assertThat(duplicate).bodyJson().extractingPath("$.detail").isEqualTo("Email already registered");
	}

	@Test
	void invalidRegistrationReturnsFieldErrors() {
		String json = """
				{"email": "not-an-email", "password": "short", "fullName": ""}""";

		MvcTestResult result = mvc.post().uri("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json");
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
				.contains("email", "password", "confirmPassword", "fullName");
		// The submitted password must never be echoed back.
		assertThat(body(result)).doesNotContain("short");
	}

	@Test
	void passwordOverBcryptByteLimitIsRejected() {
		// 37 characters with a letter and a digit pass the password rule, but in UTF-8 it is 73 bytes.
		String password = "é".repeat(36) + "1";

		assertThat(register(mvc, uniqueEmail(), password)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(login(mvc, uniqueEmail(), password)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginReturnsVerifiableToken() {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);

		MvcTestResult result = login(mvc, email, USER_PASSWORD);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
		Jwt jwt = jwtDecoder.decode(JsonPath.read(body(result), "$.accessToken"));
		assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
	}

	@Test
	void wrongPasswordAndUnknownEmailGetIdenticalResponses() {
		String email = uniqueEmail();
		register(mvc, email, USER_PASSWORD);

		MvcTestResult wrongPassword = login(mvc, email, "wrong-password-123");
		MvcTestResult unknownEmail = login(mvc, uniqueEmail(), USER_PASSWORD);

		assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(wrongPassword)).isEqualTo(body(unknownEmail));
	}

	@Test
	void bootstrapAdminLogsInWithAdminRole() {
		Jwt jwt = jwtDecoder.decode(adminToken(mvc));

		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
	}

	@Test
	void weakPasswordsAreRejected() {
		for (String weak : new String[] { "abcdefgh", "12345678", "short1" }) {
			MvcTestResult result = register(mvc, uniqueEmail(), weak);

			assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsOnly("password");
		}
	}

	@Test
	void passwordConfirmationMustMatch() {
		String json = """
				{"email": "%s", "password": "%s", "confirmPassword": "other-password-1", "fullName": "Jane"}"""
			.formatted(uniqueEmail(), USER_PASSWORD);

		MvcTestResult result = mvc.post().uri("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsOnly("passwordConfirmed");
	}

	@Test
	void registrationStartsUnverifiedAndSendsVerificationLink() {
		String email = uniqueEmail();
		String json = """
				{"email": "%s", "password": "%s", "confirmPassword": "%s", "fullName": " Jane Doe ",
				 "phone": " +84 90 123 4567 "}""".formatted(email, USER_PASSWORD, USER_PASSWORD);

		MvcTestResult result = mvc.post().uri("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.emailVerified").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$.fullName").isEqualTo("Jane Doe");
		assertThat(result).bodyJson().extractingPath("$.phone").isEqualTo("+84 90 123 4567");
		assertThat(RecordingAuthMailer.verificationLink(email)).startsWith("http://localhost:5173/verify-email?token=");
	}

	@Test
	void unverifiedAccountSignsInOnlyAfterUsingTheLink() {
		String email = uniqueEmail();
		register(mvc, email, USER_PASSWORD);

		MvcTestResult beforeVerifying = login(mvc, email, USER_PASSWORD);
		assertThat(beforeVerifying).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(beforeVerifying).bodyJson().extractingPath("$.detail").isEqualTo("Email not verified");
		// A wrong password still gets the generic answer, so the 403 reveals nothing without the password.
		assertThat(login(mvc, email, "wrong-password-1")).bodyJson().extractingPath("$.detail")
				.isEqualTo("Invalid email or password");

		assertThat(verifyEmail(mvc, RecordingAuthMailer.verificationToken(email))).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(login(mvc, email, USER_PASSWORD)).hasStatusOk();
	}

	@Test
	void verificationLinkWorksOnce() {
		String email = uniqueEmail();
		register(mvc, email, USER_PASSWORD);
		String token = RecordingAuthMailer.verificationToken(email);
		assertThat(verifyEmail(mvc, token)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult again = verifyEmail(mvc, token);

		assertThat(again).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(again).bodyJson().extractingPath("$.detail").isEqualTo("Invalid or expired link");
	}

	@Test
	void expiredOrUnknownVerificationLinkIsRejected() {
		String email = uniqueEmail();
		register(mvc, email, USER_PASSWORD);
		String token = RecordingAuthMailer.verificationToken(email);
		jdbc.sql("update user_tokens set expires_at = now() - interval '1 minute' where token_hash = :hash")
			.param("hash", OpaqueTokens.hash(token))
			.update();

		assertThat(verifyEmail(mvc, token)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(verifyEmail(mvc, OpaqueTokens.generate())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(login(mvc, email, USER_PASSWORD)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void resendReplacesThePreviousLink() {
		String email = uniqueEmail();
		register(mvc, email, USER_PASSWORD);
		String first = RecordingAuthMailer.verificationToken(email);

		assertThat(resend(email.toUpperCase(Locale.ROOT))).hasStatus(HttpStatus.ACCEPTED);
		String second = RecordingAuthMailer.verificationToken(email);

		assertThat(second).isNotEqualTo(first);
		assertThat(verifyEmail(mvc, first)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(verifyEmail(mvc, second)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void resendAnswersTheSameForUnknownAndVerifiedEmails() {
		String unknown = uniqueEmail();
		String verified = uniqueEmail();
		registerVerified(mvc, verified, USER_PASSWORD);
		String usedLink = RecordingAuthMailer.verificationLink(verified);

		assertThat(resend(unknown)).hasStatus(HttpStatus.ACCEPTED);
		assertThat(resend(verified)).hasStatus(HttpStatus.ACCEPTED);

		assertThat(RecordingAuthMailer.verificationLink(unknown)).isNull();
		assertThat(RecordingAuthMailer.verificationLink(verified)).isEqualTo(usedLink);
	}

	private MvcTestResult resend(String email) {
		return mvc.post().uri("/api/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s"}""".formatted(email))
			.exchange();
	}

}
