package com.company.project.common.ratelimit;

import static com.company.project.support.TestAuth.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The limits from application.yml, end to end. The test profile exempts MockMvc's 127.0.0.1, so each test sends from
 * its own documentation address: buckets live as long as the shared application context.
 */
@IntegrationTest
class RateLimitFilterTest {

	private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

	@Autowired
	MockMvcTester mvc;

	@Test
	void loginIsLimitedBeforeTheBodyIsRead() {
		String address = uniqueAddress();
		for (int i = 0; i < 10; i++) {
			assertThat(login(address)).hasStatus(HttpStatus.BAD_REQUEST);
		}

		assertTooManyRequests(login(address), 60);
	}

	@Test
	void anotherAddressIsNotLimited() {
		String limited = uniqueAddress();
		for (int i = 0; i < 10; i++) {
			login(limited);
		}
		assertThat(login(limited)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);

		assertThat(login(uniqueAddress())).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void resendAndForgotShareOneLimit() {
		String address = uniqueAddress();
		for (int i = 0; i < 5; i++) {
			assertThat(emailRequest(address, "/api/auth/verify-email/resend")).hasStatus(HttpStatus.ACCEPTED);
			assertThat(emailRequest(address, "/api/auth/password/forgot")).hasStatus(HttpStatus.ACCEPTED);
		}

		assertTooManyRequests(emailRequest(address, "/api/auth/verify-email/resend"), 3600);
		assertTooManyRequests(emailRequest(address, "/api/auth/password/forgot"), 3600);
	}

	@Test
	void registrationIsLimited() {
		String address = uniqueAddress();
		for (int i = 0; i < 10; i++) {
			assertThat(post(address, "/api/auth/register", "{}")).hasStatus(HttpStatus.BAD_REQUEST);
		}

		assertTooManyRequests(post(address, "/api/auth/register", "{}"), 3600);
	}

	@Test
	void searchEndpointsShareOneLimit() {
		String address = uniqueAddress();
		String[] paths = { "/api/search/hotels", "/api/search/hotels/filters",
				"/api/hotels/no-such-hotel-" + UUID.randomUUID() + "/rooms" };

		// 60 per minute. With a bucket per endpoint, 180 requests would pass.
		AtomicInteger sent = new AtomicInteger();
		Limited limited = sendUntilLimited(() -> get(address, paths[sent.getAndIncrement() % paths.length]), 120);

		assertThat(limited.passed()).isBetween(60, 119);
		assertTooManyRequests(limited.result(), 60);
	}

	@Test
	void everyRequestCountsTowardTheGeneralLimit() {
		String address = uniqueAddress();

		Limited limited = sendUntilLimited(() -> get(address, "/api/auth/me"), 600);

		assertThat(limited.passed()).isBetween(300, 599);
		assertTooManyRequests(limited.result(), 60);
		// Its own bucket is untouched, but the request also needs one from general (one comes back every 200 ms).
		assertThat(login(address)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(get(address, "/actuator/health")).hasStatus(HttpStatus.OK);
	}

	/**
	 * For limits that refill quickly (general: one request every 200 ms, search: one a second), so some come back
	 * while the loop runs: sends until the first 429 and reports how many passed before it.
	 */
	private static Limited sendUntilLimited(Supplier<MvcTestResult> request, int maxRequests) {
		for (int passed = 0; passed < maxRequests; passed++) {
			MvcTestResult result = request.get();
			if (result.getResponse().getStatus() == HttpStatus.TOO_MANY_REQUESTS.value()) {
				return new Limited(passed, result);
			}
		}
		throw new AssertionError("No 429 within " + maxRequests + " requests");
	}

	private record Limited(int passed, MvcTestResult result) {
	}

	private MvcTestResult login(String address) {
		return post(address, "/api/auth/login", "{}");
	}

	private MvcTestResult emailRequest(String address, String path) {
		return post(address, path, """
				{"email": "%s"}""".formatted(uniqueEmail()));
	}

	private MvcTestResult post(String address, String path, String json) {
		return mvc.post().uri(path).with(from(address)).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult get(String address, String path) {
		return mvc.get().uri(path).with(from(address)).exchange();
	}

	private static void assertTooManyRequests(MvcTestResult result, long maxRetryAfterSeconds) {
		assertThat(result).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json");
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(429);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Too many requests, retry later");
		assertThat(Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)))
				.isBetween(1L, maxRetryAfterSeconds);
	}

	/** TEST-NET-3 (RFC 5737): never a real client, and never MockMvc's exempt 127.0.0.1. */
	private static String uniqueAddress() {
		return "203.0.113." + NEXT_ADDRESS.incrementAndGet();
	}

	private static RequestPostProcessor from(String address) {
		return request -> {
			request.setRemoteAddr(address);
			return request;
		};
	}

}
