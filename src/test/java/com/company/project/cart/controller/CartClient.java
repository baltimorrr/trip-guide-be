package com.company.project.cart.controller;

import static com.company.project.support.TestAuth.USER_PASSWORD;
import static com.company.project.support.TestAuth.accessToken;
import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.registerVerified;
import static com.company.project.support.TestAuth.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.company.project.support.CatalogFixtures;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** A freshly signed-up user calling the cart endpoints, so a test's cart holds only what the test put in it. */
final class CartClient {

	final long userId;

	private final MockMvcTester mvc;

	private final String token;

	private CartClient(MockMvcTester mvc, String token, long userId) {
		this.mvc = mvc;
		this.token = token;
		this.userId = userId;
	}

	static CartClient signUp(MockMvcTester mvc, CatalogFixtures catalog) {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);
		return new CartClient(mvc, accessToken(mvc, email, USER_PASSWORD), catalog.userId(email));
	}

	/** One item of an add or merge body: {@code adults} and {@code childAges} per room. */
	static String item(long ratePlanId, LocalDate checkIn, LocalDate checkOut, int quantity, int adults,
			Integer... childAges) {
		return """
				{"ratePlanId": %d, "checkIn": "%s", "checkOut": "%s", "quantity": %d, "adults": %d, "childAges": [%s]}"""
			.formatted(ratePlanId, checkIn, checkOut, quantity, adults,
					Arrays.stream(childAges).map(String::valueOf).collect(Collectors.joining(", ")));
	}

	MvcTestResult cart() {
		return mvc.get().uri("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
	}

	MvcTestResult add(long ratePlanId, LocalDate checkIn, LocalDate checkOut, int quantity, int adults,
			Integer... childAges) {
		return add(item(ratePlanId, checkIn, checkOut, quantity, adults, childAges));
	}

	MvcTestResult add(String json) {
		return mvc.post()
			.uri("/api/cart/items")
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	MvcTestResult update(long itemId, LocalDate checkIn, LocalDate checkOut, int quantity) {
		return mvc.put()
			.uri("/api/cart/items/{id}", itemId)
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"checkIn": "%s", "checkOut": "%s", "quantity": %d}""".formatted(checkIn, checkOut, quantity))
			.exchange();
	}

	MvcTestResult remove(long itemId) {
		return mvc.delete().uri("/api/cart/items/{id}", itemId).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
	}

	MvcTestResult merge(String... items) {
		return mvc.post()
			.uri("/api/cart/merge")
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\": [" + String.join(", ", items) + "]}")
			.exchange();
	}

	/** The cart's lines, group after group. */
	List<Map<String, Object>> items() {
		return items(cart(), "$");
	}

	/** The lines of the cart at {@code root} in the response ({@code "$"}, or {@code "$.cart"} for a merge). */
	static List<Map<String, Object>> items(MvcTestResult result, String root) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), root + ".groups[*].items[*]");
	}

	/** The id of the cart's only line with this rate plan. */
	long itemId(long ratePlanId) {
		return items().stream()
			.filter(item -> ((Number) item.get("ratePlanId")).longValue() == ratePlanId)
			.map(item -> ((Number) item.get("id")).longValue())
			.reduce((first, second) -> {
				throw new AssertionError("more than one line of rate plan " + ratePlanId);
			})
			.orElseThrow();
	}

	static long money(Object money) {
		return ((Number) ((Map<?, ?>) money).get("amount")).longValue();
	}

}
