package com.company.project.cart.controller;

import static com.company.project.cart.controller.CartClient.item;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Collections;
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

/** Merging the cart a guest kept in the browser into their account after sign-in (C-CART-01). */
@IntegrationTest
class CartControllerMergeTest {

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

	long otherPlan;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		client = CartClient.signUp(mvc, catalog);
		CreatedHotel hotel = catalog.hotel("Merged " + suffix).create();
		long room = catalog.roomType(hotel.id(), 2, 2, 4, 4);
		plan = catalog.defaultRatePlan(room);
		otherPlan = catalog.ratePlan(room).board("BB").create();
	}

	@Test
	void anEmptyAccountCartTakesTheBrowsersItems() {
		MvcTestResult result = client.merge(item(plan, CHECK_IN, CHECK_OUT, 1, 2),
				item(otherPlan, CHECK_IN, CHECK_OUT, 2, 1, 7));

		assertThat(result).hasStatusOk();
		assertThat(JsonPath.<List<?>>read(body(result), "$.skipped")).isEmpty();
		assertThat(CartClient.items(result, "$.cart")).extracting(line -> line.get("quantity")).containsExactly(1, 2);
		assertThat(client.items()).hasSize(2);
	}

	@Test
	void aLineInBothCartsKeepsTheLargerQuantity() {
		client.add(plan, CHECK_IN, CHECK_OUT, 2, 2);

		client.merge(item(plan, CHECK_IN, CHECK_OUT, 1, 2));
		assertThat(client.items()).singleElement().satisfies(line -> assertThat(line).containsEntry("quantity", 2));

		client.merge(item(plan, CHECK_IN, CHECK_OUT, 3, 2));
		assertThat(client.items()).singleElement().satisfies(line -> assertThat(line).containsEntry("quantity", 3));
	}

	@Test
	void mergingTwiceChangesNothing() {
		String[] browser = { item(plan, CHECK_IN, CHECK_OUT, 2, 2), item(plan, CHECK_OUT, CHECK_OUT.plusDays(1), 1, 2) };
		client.merge(browser);
		List<Map<String, Object>> once = client.items();

		MvcTestResult again = client.merge(browser);

		assertThat(again).hasStatusOk();
		assertThat(client.items()).isEqualTo(once);
	}

	@Test
	void itemsThatCantBeAddedAreSkippedWithTheirReason() {
		MvcTestResult result = client.merge(item(plan, LocalDate.now().minusDays(3), LocalDate.now().minusDays(1), 1, 2),
				item(plan, CHECK_IN, CHECK_OUT, 1, 2), item(Long.MAX_VALUE, CHECK_IN, CHECK_OUT, 1, 2),
				item(otherPlan, CHECK_IN, CHECK_OUT, 1, 3));

		assertThat(result).hasStatusOk();
		List<Map<String, Object>> skipped = JsonPath.read(body(result), "$.skipped");
		assertThat(skipped).extracting(s -> s.get("index")).containsExactly(0, 2, 3);
		assertThat(skipped).extracting(s -> s.get("reason"))
			.containsExactly("Check-in cannot be in the past", "Rate plan %d not found".formatted(Long.MAX_VALUE),
					"This room can't hold these guests");
		assertThat(client.items()).extracting(line -> ((Number) line.get("ratePlanId")).longValue()).containsExactly(plan);
	}

	@Test
	void mergedLinesCountInEachOthersRoomsCheck() {
		MvcTestResult result = client.merge(item(plan, CHECK_IN, CHECK_OUT, 3, 2),
				item(otherPlan, CHECK_IN, CHECK_OUT, 2, 2), item(otherPlan, CHECK_IN, CHECK_OUT, 1, 1));

		assertThat(JsonPath.<List<Integer>>read(body(result), "$.skipped[*].index")).containsExactly(1);
		assertThat(client.items()).extracting(line -> line.get("quantity")).containsExactly(3, 1);
	}

	@Test
	void takesAtMostAFullCartsItems() {
		String[] tooMany = Collections.nCopies(21, item(plan, CHECK_IN, CHECK_OUT, 1, 2)).toArray(String[]::new);

		assertThat(client.merge(tooMany)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.merge()).hasStatusOk().bodyJson().extractingPath("$.cart.groups").asArray().isEmpty();
		assertThat(client.merge("{\"ratePlanId\": 1}")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
