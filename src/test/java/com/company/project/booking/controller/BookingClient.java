package com.company.project.booking.controller;

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

/** A freshly signed-up user who fills a cart and checks it out, so a test sees only its own cart and bookings. */
final class BookingClient {

	final long userId;

	final String email;

	private final MockMvcTester mvc;

	private final String token;

	private BookingClient(MockMvcTester mvc, String token, String email, long userId) {
		this.mvc = mvc;
		this.token = token;
		this.email = email;
		this.userId = userId;
	}

	static BookingClient signUp(MockMvcTester mvc, CatalogFixtures catalog) {
		String email = uniqueEmail();
		registerVerified(mvc, email, USER_PASSWORD);
		return new BookingClient(mvc, accessToken(mvc, email, USER_PASSWORD), email, catalog.userId(email));
	}

	/** Adds rooms to the cart ({@code adults} and {@code childAges} per room) and returns the line's id. */
	long addToCart(long ratePlanId, LocalDate checkIn, LocalDate checkOut, int quantity, int adults,
			Integer... childAges) {
		MvcTestResult result = mvc.post()
			.uri("/api/cart/items")
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"ratePlanId": %d, "checkIn": "%s", "checkOut": "%s", "quantity": %d, "adults": %d, "childAges": [%s]}"""
				.formatted(ratePlanId, checkIn, checkOut, quantity, adults,
						Arrays.stream(childAges).map(String::valueOf).collect(Collectors.joining(", "))))
			.exchange();
		assertThat(result).hasStatusOk();
		List<Integer> ids = JsonPath.read(body(result), "$.groups[?(@.checkIn == '%s' && @.checkOut == '%s')].items[?(@.ratePlanId == %d)].id"
			.formatted(checkIn, checkOut, ratePlanId));
		assertThat(ids).hasSize(1);
		return ids.getFirst();
	}

	MvcTestResult cart() {
		return mvc.get().uri("/api/cart").header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
	}

	/** {@code code} null for none. */
	MvcTestResult quote(String code, long... cartItemIds) {
		return post("/api/bookings/quote", """
				{"cartItemIds": %s, "discountCode": %s}""".formatted(ids(cartItemIds), json(code)));
	}

	MvcTestResult book(String json) {
		return post("/api/bookings", json);
	}

	MvcTestResult book(BookingBody body) {
		return book(body.json());
	}

	/** A booking of these lines at {@code expectedTotal} (a Money object), with valid booker details and terms. */
	static BookingBody booking(String expectedTotal, long... cartItemIds) {
		return new BookingBody(expectedTotal, cartItemIds);
	}

	/** The quote's total, to send back as the booking's {@code expectedTotal}. */
	static String total(MvcTestResult quote) {
		assertThat(quote).hasStatusOk();
		Map<String, Object> total = JsonPath.read(body(quote), "$.price.total");
		return "{\"amount\": %s, \"currency\": \"%s\"}".formatted(total.get("amount"), total.get("currency"));
	}

	/** Saves the profile the quote pre-fills the booker from. */
	void profile(String fullName, String phone, String countryCode) {
		assertThat(mvc.put()
			.uri("/api/auth/me")
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName": %s, "phone": %s, "countryCode": %s}""".formatted(json(fullName), json(phone),
					json(countryCode)))
			.exchange()).hasStatusOk();
	}

	static String ids(long... cartItemIds) {
		return Arrays.stream(cartItemIds).mapToObj(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
	}

	static String json(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	static long money(Object money) {
		return ((Number) ((Map<?, ?>) money).get("amount")).longValue();
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	/** A booking request; every field can be replaced with raw JSON, {@code null} leaves it out. */
	static final class BookingBody {

		private final String expectedTotal;

		private final long[] cartItemIds;

		private String discountCode;

		private String booker = """
				{"fullName": "Lan Nguyen", "email": "Lan@Example.com", "phone": "+84901234567", "countryCode": "vn"}""";

		private String guestName;

		private String specialRequests;

		private String estimatedArrival;

		private String leadGuests;

		private String acceptedTerms = "true";

		private BookingBody(String expectedTotal, long[] cartItemIds) {
			this.expectedTotal = expectedTotal;
			this.cartItemIds = cartItemIds;
		}

		BookingBody code(String code) {
			this.discountCode = BookingClient.json(code);
			return this;
		}

		BookingBody booker(String json) {
			this.booker = json;
			return this;
		}

		BookingBody guest(String guestName, String specialRequests, String estimatedArrival) {
			this.guestName = BookingClient.json(guestName);
			this.specialRequests = BookingClient.json(specialRequests);
			this.estimatedArrival = BookingClient.json(estimatedArrival);
			return this;
		}

		/** Raw JSON for {@code leadGuests}, e.g. {@code [{"cartItemId": 1, "names": ["Lan"]}]}. */
		BookingBody leadGuests(String json) {
			this.leadGuests = json;
			return this;
		}

		/** Raw JSON for {@code acceptedTerms}; null leaves it out. */
		BookingBody acceptedTerms(String json) {
			this.acceptedTerms = json;
			return this;
		}

		String json() {
			StringBuilder json = new StringBuilder("{\"cartItemIds\": ").append(ids(cartItemIds))
				.append(", \"expectedTotal\": ").append(expectedTotal);
			field(json, "discountCode", discountCode);
			field(json, "booker", booker);
			field(json, "guestName", guestName);
			field(json, "specialRequests", specialRequests);
			field(json, "estimatedArrival", estimatedArrival);
			field(json, "leadGuests", leadGuests);
			field(json, "acceptedTerms", acceptedTerms);
			return json.append('}').toString();
		}

		private static void field(StringBuilder json, String name, String value) {
			if (value != null) {
				json.append(", \"").append(name).append("\": ").append(value);
			}
		}

	}

}
