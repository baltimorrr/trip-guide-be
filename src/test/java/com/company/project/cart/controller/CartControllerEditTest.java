package com.company.project.cart.controller;

import static com.company.project.cart.controller.CartClient.money;
import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Changing a line's quantity or dates, and removing it (C-CART-03). */
@IntegrationTest
class CartControllerEditTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	CartClient client;

	long plan;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		client = CartClient.signUp(mvc, catalog);
		CreatedHotel hotel = catalog.hotel("Edited " + suffix).create();
		plan = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, 4));
	}

	@Test
	void aQuantityChangeKeepsThePriceTheUserSaw() {
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2);
		long item = client.itemId(plan);
		catalog.nightPrice(plan, CHECK_IN, 1_400_000, null, false);

		MvcTestResult result = client.update(item, CHECK_IN, CHECK_OUT, 3);

		assertThat(result).hasStatusOk();
		Map<String, Object> line = CartClient.items(result, "$").getFirst();
		assertThat(line).containsEntry("quantity", 3).containsEntry("priceChanged", true);
		assertThat(money(line.get("priceAtAdd"))).isEqualTo(2 * DEFAULT_PRICE);
		assertThat(money(line.get("totalPrice"))).isEqualTo(3 * (1_400_000 + DEFAULT_PRICE));
	}

	@Test
	void newDatesMoveTheLineToItsGroupAtTodaysPrice() {
		client.add(plan, CHECK_IN, CHECK_OUT, 2, 2);
		long item = client.itemId(plan);
		LocalDate later = CHECK_IN.plusDays(7);
		catalog.nightPrice(plan, later, 1_300_000, null, false);

		MvcTestResult result = client.update(item, later, later.plusDays(3), 2);

		assertThat(result).hasStatusOk();
		assertThat(JsonPath.<List<String>>read(body(result), "$.groups[*].checkIn")).containsExactly(later.toString());
		Map<String, Object> line = CartClient.items(result, "$").getFirst();
		assertThat(line).containsEntry("id", (int) item).containsEntry("priceChanged", false);
		assertThat(money(line.get("priceAtAdd"))).isEqualTo(1_300_000 + 2 * DEFAULT_PRICE);
		assertThat(JsonPath.<Integer>read(body(result), "$.groups[0].nights")).isEqualTo(3);
	}

	@Test
	void newDatesOfAnotherLineMergeTheTwo() {
		LocalDate later = CHECK_IN.plusDays(7);
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2);
		client.add(plan, later, later.plusDays(2), 2, 2);
		List<Map<String, Object>> before = client.items();
		long moved = ((Number) before.get(0).get("id")).longValue();
		long kept = ((Number) before.get(1).get("id")).longValue();

		MvcTestResult result = client.update(moved, later, later.plusDays(2), 1);

		assertThat(result).hasStatusOk();
		assertThat(CartClient.items(result, "$")).singleElement().satisfies(line -> assertThat(line)
			.containsEntry("id", (int) kept)
			.containsEntry("quantity", 3));
	}

	@Test
	void anEditThatCantBeBookedChangesNothing() {
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2);
		long item = client.itemId(plan);
		LocalDate closed = CHECK_IN.plusDays(10);
		catalog.nightPrice(plan, closed, DEFAULT_PRICE, null, true);
		List<Map<String, Object>> before = client.items();

		assertThat(client.update(item, closed, closed.plusDays(1), 1)).hasStatus(HttpStatus.CONFLICT);
		assertThat(client.update(item, CHECK_IN, CHECK_OUT, 5)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Only 4 rooms of this type are left for these dates");
		assertThat(client.update(item, LocalDate.now().minusDays(1), CHECK_OUT, 1)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.update(item, CHECK_IN, CHECK_OUT, 0)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.items()).isEqualTo(before);
	}

	@Test
	void anotherUsersLineCanBeNeitherChangedNorRemoved() {
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2);
		long item = client.itemId(plan);
		CartClient other = CartClient.signUp(mvc, catalog);

		assertThat(other.update(item, CHECK_IN, CHECK_OUT, 2)).hasStatus(HttpStatus.NOT_FOUND);
		other.add(plan, CHECK_IN, CHECK_OUT, 1, 2);
		assertThat(other.update(item, CHECK_IN, CHECK_OUT, 2)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(other.remove(item)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(client.items()).singleElement().satisfies(line -> assertThat(line).containsEntry("quantity", 1));
	}

	@Test
	void removingIsIdempotent() {
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2);
		long item = client.itemId(plan);

		assertThat(client.remove(item)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(client.remove(item)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(client.items()).isEmpty();
		assertThat(client.update(item, CHECK_IN, CHECK_OUT, 1)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void needsASignedInUser() {
		assertThat(mvc.put().uri("/api/cart/items/1").contentType("application/json").content("{}"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete().uri("/api/cart/items/1")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/cart/merge").contentType("application/json").content("{\"items\": []}"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
