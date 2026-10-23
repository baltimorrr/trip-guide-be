package com.company.project.auth.controller;

import static com.company.project.support.TestAuth.USER_PASSWORD;
import static com.company.project.support.TestAuth.accessToken;
import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.login;
import static com.company.project.support.TestAuth.registerVerified;
import static com.company.project.support.TestAuth.uniqueEmail;
import static com.company.project.support.TestAuth.userToken;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The signed-in user's profile (GET/PUT /api/auth/me) and password change. */
@IntegrationTest
class ProfileControllerTest {

	@Autowired
	MockMvcTester mvc;

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
		registerVerified(mvc, email, USER_PASSWORD);
		String token = accessToken(mvc, email, USER_PASSWORD);

		assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.email")
				.isEqualTo(email);
	}

	@Test
	void newAccountProfileShowsVerifiedEmailAndEmptyOptionalFields() {
		assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken(mvc))))
				.hasStatusOk()
				.bodyJson()
				.satisfies(json -> {
					assertThat(json).extractingPath("$.emailVerified").isEqualTo(true);
					assertThat(json).extractingPath("$.phone").isNull();
					assertThat(json).extractingPath("$.countryCode").isNull();
					assertThat(json).extractingPath("$.preferredCurrency").isNull();
				});
	}

	@Test
	void profileUpdateIsNormalizedAndReadBack() {
		String token = userToken(mvc);

		MvcTestResult updated = updateProfile(token, """
				{"fullName": " Jane Doe ", "phone": " +84 90 123 4567 ", "countryCode": "vn", "preferredCurrency": "usd"}""");

		assertThat(updated).hasStatusOk();
		assertThat(updated).bodyJson().extractingPath("$.fullName").isEqualTo("Jane Doe");
		assertThat(updated).bodyJson().extractingPath("$.phone").isEqualTo("+84 90 123 4567");
		assertThat(updated).bodyJson().extractingPath("$.countryCode").isEqualTo("VN");
		assertThat(updated).bodyJson().extractingPath("$.preferredCurrency").isEqualTo("USD");
		assertThat(mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.bodyJson().extractingPath("$.countryCode").isEqualTo("VN");
	}

	@Test
	void fieldsLeftOutOfAnUpdateAreCleared() {
		String token = userToken(mvc);
		updateProfile(token, """
				{"fullName": "Jane", "phone": "0901234567", "countryCode": "VN", "preferredCurrency": "VND"}""");

		MvcTestResult updated = updateProfile(token, """
				{"fullName": "Jane"}""");

		assertThat(updated).hasStatusOk();
		assertThat(updated).bodyJson().extractingPath("$.phone").isNull();
		assertThat(updated).bodyJson().extractingPath("$.preferredCurrency").isNull();
	}

	@Test
	void invalidProfileFieldsAreNamed() {
		MvcTestResult result = updateProfile(userToken(mvc), """
				{"fullName": " ", "phone": "call me", "countryCode": "XX", "preferredCurrency": "ABC"}""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
				.containsExactlyInAnyOrder("fullName", "phone", "countryCode", "preferredCurrency");
	}

	@Test
	void profileNeedsAnAccessToken() {
		assertThat(mvc.put().uri("/api/auth/me").contentType(MediaType.APPLICATION_JSON).content("""
				{"fullName": "Jane"}""")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void changingThePasswordKeepsThisDeviceAndSignsOutTheOthers() {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);
		String thisDevice = accessToken(mvc, email, USER_PASSWORD);
		MvcTestResult otherLogin = login(mvc, email, USER_PASSWORD);
		String otherDevice = JsonPath.read(body(otherLogin), "$.accessToken");
		String otherRefresh = JsonPath.read(body(otherLogin), "$.refreshToken");

		assertThat(changePassword(thisDevice, USER_PASSWORD, "brand-new-password-2")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(me(thisDevice)).hasStatusOk();
		assertThat(me(otherDevice)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken": "%s"}""".formatted(otherRefresh)))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login(mvc, email, USER_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login(mvc, email, "brand-new-password-2")).hasStatusOk();
	}

	@Test
	void wrongCurrentPasswordChangesNothing() {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);
		String thisDevice = accessToken(mvc, email, USER_PASSWORD);
		String otherDevice = accessToken(mvc, email, USER_PASSWORD);

		MvcTestResult result = changePassword(thisDevice, "not-my-password-1", "brand-new-password-2");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Current password is incorrect");
		assertThat(me(otherDevice)).hasStatusOk();
		assertThat(login(mvc, email, USER_PASSWORD)).hasStatusOk();
	}

	@Test
	void weakNewPasswordIsRejected() {
		MvcTestResult result = changePassword(userToken(mvc), USER_PASSWORD, "password");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsOnly("newPassword");
	}

	private MvcTestResult me(String token) {
		return mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
	}

	private MvcTestResult updateProfile(String token, String json) {
		return mvc.put().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult changePassword(String token, String current, String next) {
		return mvc.put().uri("/api/auth/me/password").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword": "%s", "newPassword": "%s"}""".formatted(current, next))
				.exchange();
	}

}
