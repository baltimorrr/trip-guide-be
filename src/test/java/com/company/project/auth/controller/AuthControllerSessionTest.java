package com.company.project.auth.controller;

import static com.company.project.support.TestAuth.USER_PASSWORD;
import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.registerVerified;
import static com.company.project.support.TestAuth.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;

import com.company.project.auth.security.OpaqueTokens;
import com.company.project.auth.security.TokenService;
import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Sign-in sessions: token pair, refresh rotation and reuse detection, sign-out of one or every device. */
@IntegrationTest
class AuthControllerSessionTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	JdbcClient jdbc;

	@Test
	void signInOpensASessionThatStoresOnlyTheTokenHash() {
		String email = verifiedUser();

		MvcTestResult result = mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.header(HttpHeaders.USER_AGENT, "JUnit browser")
			.content(credentials(email, false))
			.exchange();

		assertThat(result).hasStatusOk();
		Tokens tokens = Tokens.of(result);
		assertThat(tokens.refresh()).hasSize(43);
		assertThat(JsonPath.<Integer>read(body(result), "$.expiresIn")).isEqualTo(900);
		assertThat(JsonPath.<Integer>read(body(result), "$.refreshExpiresIn"))
			.isCloseTo((int) Duration.ofDays(1).toSeconds(), within(5));
		String sessionId = jwtDecoder.decode(tokens.access()).getClaimAsString(TokenService.SESSION_CLAIM);
		var row = jdbc.sql("select refresh_token_hash, user_agent, ip from user_sessions where id = :id")
			.param("id", Long.valueOf(sessionId))
			.query()
			.singleRow();
		assertThat(row.get("refresh_token_hash")).isEqualTo(OpaqueTokens.hash(tokens.refresh()));
		assertThat(row.get("user_agent")).isEqualTo("JUnit browser");
		assertThat(row.get("ip")).isEqualTo("127.0.0.1");
	}

	@Test
	void rememberMeKeepsTheSessionForThirtyDays() {
		String email = verifiedUser();

		MvcTestResult result = login(email, true);

		assertThat(JsonPath.<Integer>read(body(result), "$.refreshExpiresIn"))
			.isCloseTo((int) Duration.ofDays(30).toSeconds(), within(5));
	}

	@Test
	void refreshHandsOutANewPairForTheSameSession() {
		Tokens first = Tokens.of(login(verifiedUser(), false));

		MvcTestResult refreshed = refresh(first.refresh());

		assertThat(refreshed).hasStatusOk();
		Tokens second = Tokens.of(refreshed);
		assertThat(second.refresh()).isNotEqualTo(first.refresh());
		assertThat(sessionOf(second.access())).isEqualTo(sessionOf(first.access()));
		assertThat(me(second.access())).hasStatusOk();
		assertThat(refresh(second.refresh())).hasStatusOk();
	}

	@Test
	void replayedRefreshTokenRevokesTheSession() {
		Tokens first = Tokens.of(login(verifiedUser(), false));
		Tokens second = Tokens.of(refresh(first.refresh()));

		MvcTestResult replay = refresh(first.refresh());

		assertThat(replay).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(replay).bodyJson().extractingPath("$.detail").isEqualTo("Invalid or expired refresh token");
		// Whoever holds the newest tokens is cut off too.
		assertThat(refresh(second.refresh())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(me(second.access())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void unknownOrEndedSessionCannotRefresh() {
		Tokens tokens = Tokens.of(login(verifiedUser(), false));
		jdbc.sql("update user_sessions set expires_at = now() - interval '1 second' where refresh_token_hash = :hash")
			.param("hash", OpaqueTokens.hash(tokens.refresh()))
			.update();

		assertThat(refresh(tokens.refresh())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(me(tokens.access())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(refresh(OpaqueTokens.generate())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void signOutEndsOnlyThisDevice() {
		String email = verifiedUser();
		Tokens phone = Tokens.of(login(email, false));
		Tokens laptop = Tokens.of(login(email, false));

		assertThat(logout(phone.refresh())).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult phoneMe = me(phone.access());
		assertThat(phoneMe).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(phoneMe).bodyJson().extractingPath("$.detail").isEqualTo("Invalid or expired access token");
		assertThat(refresh(phone.refresh())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(me(laptop.access())).hasStatusOk();
		assertThat(refresh(laptop.refresh())).hasStatusOk();
	}

	@Test
	void signOutWithAnUnknownTokenStillSucceeds() {
		assertThat(logout(OpaqueTokens.generate())).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void signOutEverywhereEndsEveryDevice() {
		String email = verifiedUser();
		Tokens phone = Tokens.of(login(email, false));
		Tokens laptop = Tokens.of(login(email, false));

		assertThat(mvc.post().uri("/api/auth/logout-all").header(HttpHeaders.AUTHORIZATION, bearer(phone.access())))
			.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(me(phone.access())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(me(laptop.access())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(refresh(laptop.refresh())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void signOutEverywhereNeedsAnAccessToken() {
		assertThat(mvc.post().uri("/api/auth/logout-all")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void publicAccountEndpointsIgnoreAStaleAuthorizationHeader() {
		String email = verifiedUser();

		MvcTestResult login = mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.header(HttpHeaders.AUTHORIZATION, bearer("expired-or-garbage"))
			.content(credentials(email, false))
			.exchange();
		assertThat(login).hasStatusOk();

		MvcTestResult refresh = mvc.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.header(HttpHeaders.AUTHORIZATION, bearer("expired-or-garbage"))
			.content(refreshJson(Tokens.of(login).refresh()))
			.exchange();
		assertThat(refresh).hasStatusOk();
	}

	@Test
	void disabledUserLosesAccessAtOnce() {
		String email = verifiedUser();
		Tokens tokens = Tokens.of(login(email, false));

		jdbc.sql("update users set enabled = false where email = :email").param("email", email).update();

		assertThat(me(tokens.access())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(refresh(tokens.refresh())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private record Tokens(String access, String refresh) {

		static Tokens of(MvcTestResult result) {
			assertThat(result).hasStatusOk();
			return new Tokens(JsonPath.read(body(result), "$.accessToken"), JsonPath.read(body(result), "$.refreshToken"));
		}

	}

	private String verifiedUser() {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);
		return email;
	}

	private String sessionOf(String accessToken) {
		return jwtDecoder.decode(accessToken).getClaimAsString(TokenService.SESSION_CLAIM);
	}

	private MvcTestResult login(String email, boolean rememberMe) {
		return mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(credentials(email, rememberMe))
			.exchange();
	}

	private MvcTestResult refresh(String refreshToken) {
		return mvc.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(refreshJson(refreshToken))
			.exchange();
	}

	private MvcTestResult logout(String refreshToken) {
		return mvc.post().uri("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
			.content(refreshJson(refreshToken))
			.exchange();
	}

	private MvcTestResult me(String accessToken) {
		return mvc.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)).exchange();
	}

	private static String credentials(String email, boolean rememberMe) {
		return """
				{"email": "%s", "password": "%s", "rememberMe": %s}""".formatted(email, USER_PASSWORD, rememberMe);
	}

	private static String refreshJson(String refreshToken) {
		return """
				{"refreshToken": "%s"}""".formatted(refreshToken);
	}

}
