package com.company.project.auth.controller;

import static com.company.project.support.TestAuth.USER_PASSWORD;
import static com.company.project.support.TestAuth.accessToken;
import static com.company.project.support.TestAuth.adminToken;
import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.login;
import static com.company.project.support.TestAuth.register;
import static com.company.project.support.TestAuth.uniqueEmail;
import static com.company.project.support.TestAuth.userToken;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
				.contains("email", "password", "fullName");
		// The submitted password must never be echoed back.
		assertThat(body(result)).doesNotContain("short");
	}

	@Test
	void passwordOverBcryptByteLimitIsRejected() {
		// 37 characters passes @Size(max = 72), but in UTF-8 it is 74 bytes.
		String password = "é".repeat(37);

		assertThat(register(mvc, uniqueEmail(), password)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(login(mvc, uniqueEmail(), password)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginReturnsVerifiableToken() {
		String email = uniqueEmail();
		register(mvc, email, USER_PASSWORD);

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
	void meWithoutTokenReturnsBearerChallenge() {
		MvcTestResult result = mvc.get().uri("/api/auth/me").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
		assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json");
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Authentication required");
	}

	@Test
	void meWithMalformedTokenReturnsProblemDetail() {
		MvcTestResult result = mvc.get().uri("/api/auth/me")
				.header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Invalid or expired access token");
	}

	@Test
	void meWithTamperedSignatureIsRejected() {
		String token = userToken(mvc);
		// Change a character inside the signature (not the last one, whose low bits are padding).
		int index = token.lastIndexOf('.') + 5;
		char replacement = token.charAt(index) == 'A' ? 'B' : 'A';
		String tampered = token.substring(0, index) + replacement + token.substring(index + 1);

		MvcTestResult result = mvc.get().uri("/api/auth/me")
				.header(HttpHeaders.AUTHORIZATION, bearer(tampered)).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void meReturnsCurrentUser() {
		String email = uniqueEmail();
		register(mvc, email, USER_PASSWORD);
		String token = accessToken(mvc, email, USER_PASSWORD);

		assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.email")
				.isEqualTo(email);
	}

	@Test
	void bootstrapAdminLogsInWithAdminRole() {
		Jwt jwt = jwtDecoder.decode(adminToken(mvc));

		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
	}

}
