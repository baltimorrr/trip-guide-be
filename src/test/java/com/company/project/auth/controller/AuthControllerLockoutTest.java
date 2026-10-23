package com.company.project.auth.controller;

import static com.company.project.support.TestAuth.USER_PASSWORD;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.login;
import static com.company.project.support.TestAuth.registerVerified;
import static com.company.project.support.TestAuth.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Repeated failed sign-ins lock the account (app.auth.lockout: 5 within 15 minutes, locked for 15). */
@IntegrationTest
class AuthControllerLockoutTest {

	static final String WRONG_PASSWORD = "wrong-password-1";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void fiveFailuresLockTheAccountWithoutSayingSo() {
		String email = verifiedUser();
		MvcTestResult wrong = null;
		for (int attempt = 1; attempt <= 5; attempt++) {
			wrong = login(mvc, email, WRONG_PASSWORD);
			assertThat(wrong).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		MvcTestResult rightPasswordWhileLocked = login(mvc, email, USER_PASSWORD);

		assertThat(rightPasswordWhileLocked).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(rightPasswordWhileLocked)).isEqualTo(body(wrong));
	}

	@Test
	void lockEndsAfterItsTimeAndSuccessResetsTheCount() {
		String email = verifiedUser();
		for (int attempt = 1; attempt <= 5; attempt++) {
			login(mvc, email, WRONG_PASSWORD);
		}

		jdbc.sql("update users set locked_until = now() - interval '1 second' where email = :email")
			.param("email", email)
			.update();

		assertThat(login(mvc, email, USER_PASSWORD)).hasStatusOk();
		assertThat(failedCount(email)).isZero();
	}

	@Test
	void failuresOutsideTheWindowStartCountingAgain() {
		String email = verifiedUser();
		for (int attempt = 1; attempt <= 4; attempt++) {
			login(mvc, email, WRONG_PASSWORD);
		}
		jdbc.sql("update users set first_failed_login_at = now() - interval '16 minutes' where email = :email")
			.param("email", email)
			.update();

		login(mvc, email, WRONG_PASSWORD);

		assertThat(failedCount(email)).isEqualTo(1);
		assertThat(login(mvc, email, USER_PASSWORD)).hasStatusOk();
	}

	@Test
	void overlongPasswordCountsAsAFailure() {
		String email = verifiedUser();

		assertThat(login(mvc, email, "é".repeat(37))).hasStatus(HttpStatus.UNAUTHORIZED);

		assertThat(failedCount(email)).isEqualTo(1);
	}

	@Test
	void successfulSignInForgetsEarlierFailures() {
		String email = verifiedUser();
		for (int attempt = 1; attempt <= 3; attempt++) {
			login(mvc, email, WRONG_PASSWORD);
		}

		assertThat(login(mvc, email, USER_PASSWORD)).hasStatusOk();

		assertThat(failedCount(email)).isZero();
	}

	private String verifiedUser() {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);
		return email;
	}

	private int failedCount(String email) {
		// JDBC reads smallint back as Integer.
		return jdbc.sql("select failed_login_count from users where email = :email")
			.param("email", email)
			.query(Integer.class)
			.single();
	}

}
