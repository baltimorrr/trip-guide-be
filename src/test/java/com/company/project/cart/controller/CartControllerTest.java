package com.company.project.cart.controller;

import static com.company.project.cart.controller.CartClient.item;
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

/**
 * Adding rooms to the cart and reading it back grouped by hotel and date range (C-CART-01). Every test signs up a new
 * user and builds its own hotels, so the cart holds only what the test added.
 */
@IntegrationTest
class CartControllerTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	CartClient client;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		client = CartClient.signUp(mvc, catalog);
	}

	@Test
	void anAddedLineIsListedInItsHotelAndDatesGroup() {
		CreatedHotel hotel = catalog.hotel("Added " + suffix).stars(4).create();
		long room = catalog.roomType(hotel.id(), 2, 2, 4, 5);
		long plan = catalog.defaultRatePlan(room);

		MvcTestResult added = client.add(plan, CHECK_IN, CHECK_OUT, 2, 2, 9, 4);

		assertThat(added).hasStatusOk();
		assertThat(added).bodyJson().extractingPath("$.groups[0].items[0].quantity").isEqualTo(2);
		MvcTestResult result = client.cart();
		assertThat(result).hasStatusOk();
		Map<String, Object> group = JsonPath.read(body(result), "$.groups[0]");
		assertThat(JsonPath.<List<?>>read(body(result), "$.groups")).hasSize(1);
		assertThat(group).containsEntry("checkIn", CHECK_IN.toString())
			.containsEntry("checkOut", CHECK_OUT.toString())
			.containsEntry("nights", 2)
			.containsEntry("taxesIncluded", true)
			.containsEntry("canCheckout", true);
		assertThat(money(group.get("total"))).isEqualTo(2 * 2 * DEFAULT_PRICE);
		Map<String, Object> cartHotel = JsonPath.read(body(result), "$.groups[0].hotel");
		assertThat(cartHotel).containsEntry("slug", hotel.slug())
			.containsEntry("name", hotel.name())
			.containsEntry("starRating", 4)
			.containsEntry("listed", true);
		Map<String, Object> line = JsonPath.read(body(result), "$.groups[0].items[0]");
		assertThat(line).containsEntry("roomTypeId", (int) room)
			.containsEntry("boardType", "RO")
			.containsEntry("quantity", 2)
			.containsEntry("adults", 2)
			.containsEntry("childAges", List.of(4, 9))
			.containsEntry("status", "AVAILABLE")
			.containsEntry("roomsLeft", 5)
			.containsEntry("priceChanged", false);
		assertThat(((Number) line.get("ratePlanId")).longValue()).isEqualTo(plan);
		assertThat(money(line.get("pricePerRoom"))).isEqualTo(2 * DEFAULT_PRICE);
		assertThat(money(line.get("totalPrice"))).isEqualTo(2 * 2 * DEFAULT_PRICE);
		assertThat(money(line.get("priceAtAdd"))).isEqualTo(2 * DEFAULT_PRICE);
		assertThat(JsonPath.<Boolean>read(body(result), "$.groups[0].items[0].cancellation.refundable")).isTrue();
	}

	@Test
	void addingTheSameLineAgainAddsUpTheRooms() {
		long plan = plan(catalog.hotel("Again " + suffix).create(), 5);

		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2, 9, 4);
		MvcTestResult again = client.add(plan, CHECK_IN, CHECK_OUT, 2, 2, 4, 9);

		assertThat(again).hasStatusOk();
		assertThat(client.items()).singleElement().satisfies(line -> assertThat(line).containsEntry("quantity", 3));
		// Other guests are another line.
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 1);
		assertThat(client.items()).extracting(line -> line.get("quantity")).containsExactly(3, 1);
	}

	@Test
	void groupsLinesByHotelAndDateRange() {
		CreatedHotel a = catalog.hotel("A " + suffix).create();
		CreatedHotel b = catalog.hotel("B " + suffix).create();
		long planA = plan(a, 5);
		long planB = plan(b, 5);
		LocalDate later = CHECK_IN.plusDays(10);

		client.add(planA, later, later.plusDays(1), 1, 2);
		client.add(planB, CHECK_IN, CHECK_OUT, 1, 2);
		client.add(planA, CHECK_IN, CHECK_OUT, 1, 2);

		MvcTestResult result = client.cart();
		assertThat(JsonPath.<List<String>>read(body(result), "$.groups[*].hotel.slug"))
			.containsExactly(a.slug(), b.slug(), a.slug());
		assertThat(JsonPath.<List<String>>read(body(result), "$.groups[*].checkIn"))
			.containsExactly(CHECK_IN.toString(), CHECK_IN.toString(), later.toString());
		assertThat(JsonPath.<List<Integer>>read(body(result), "$.groups[*].nights")).containsExactly(2, 2, 1);
	}

	@Test
	void anUnknownRatePlanIsNotFound() {
		assertThat(client.add(Long.MAX_VALUE, CHECK_IN, CHECK_OUT, 1, 2)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectsStaysTheSearchWouldRejectAndLeavesTheCartAsItWas() {
		long plan = plan(catalog.hotel("Invalid " + suffix).create(), 10);
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2);
		List<Map<String, Object>> before = client.items();

		assertThat(client.add(plan, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), 1, 2))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.add(plan, CHECK_IN, CHECK_IN, 1, 2)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.add(plan, CHECK_IN, CHECK_OUT, 9, 1)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.add(plan, CHECK_IN, CHECK_OUT, 0, 2)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.add(plan, CHECK_IN, CHECK_OUT, 1, 2, 18)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.add("""
				{"ratePlanId": %d, "checkIn": "%s", "checkOut": "%s", "quantity": 1}""".formatted(plan, CHECK_IN,
				CHECK_OUT))).hasStatus(HttpStatus.BAD_REQUEST);
		// Adding up to more rooms than one search may book is rejected too.
		assertThat(client.add(plan, CHECK_IN, CHECK_OUT, 8, 2)).hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(client.items()).isEqualTo(before);
	}

	@Test
	void rejectsGuestsTheRoomCantHold() {
		CreatedHotel hotel = catalog.hotel("Small " + suffix).create();
		long plan = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 0, 2, 5));

		MvcTestResult result = client.add(plan, CHECK_IN, CHECK_OUT, 1, 3);

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("This room can't hold these guests");
		assertThat(client.add(plan, CHECK_IN, CHECK_OUT, 1, 2, 5)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(client.items()).isEmpty();
	}

	@Test
	void rejectsARateThatCantSellTheStay() {
		CreatedHotel hotel = catalog.hotel("Closed " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 0, 2, 5);
		long closed = catalog.ratePlan(room).create();
		catalog.nightPrice(closed, CHECK_IN.plusDays(1), DEFAULT_PRICE, null, true);
		long longStay = catalog.ratePlan(room).minNights(3).create();
		long inactive = catalog.ratePlan(room).inactive().create();
		long soldOutRoom = catalog.roomType(hotel.id(), 2, 0, 2, 1);
		catalog.inventory(soldOutRoom, CHECK_IN, 1, 1, false);

		assertThat(client.add(closed, CHECK_IN, CHECK_OUT, 1, 2)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("This rate can't be booked for these dates");
		assertThat(client.add(longStay, CHECK_IN, CHECK_OUT, 1, 2)).hasStatus(HttpStatus.CONFLICT);
		assertThat(client.add(inactive, CHECK_IN, CHECK_OUT, 1, 2)).hasStatus(HttpStatus.CONFLICT);
		assertThat(client.add(catalog.defaultRatePlan(soldOutRoom), CHECK_IN, CHECK_OUT, 1, 2))
			.hasStatus(HttpStatus.CONFLICT);
		catalog.hotelStatus(hotel.id(), "INACTIVE");
		assertThat(client.add(longStay, CHECK_IN, CHECK_IN.plusDays(3), 1, 2)).hasStatus(HttpStatus.CONFLICT);
		assertThat(client.items()).isEmpty();
	}

	@Test
	void aGroupsLinesOfOneRoomTypeShareTheRoomsLeft() {
		CreatedHotel hotel = catalog.hotel("Shared " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		long roomOnly = catalog.ratePlan(room).create();
		long breakfast = catalog.ratePlan(room).board("BB").create();

		assertThat(client.add(roomOnly, CHECK_IN, CHECK_OUT, 4, 2)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Only 3 rooms of this type are left for these dates");
		assertThat(client.add(roomOnly, CHECK_IN, CHECK_OUT, 2, 2)).hasStatusOk();
		assertThat(client.add(breakfast, CHECK_IN, CHECK_OUT, 2, 2)).hasStatus(HttpStatus.CONFLICT);
		assertThat(client.add(roomOnly, CHECK_IN, CHECK_OUT, 2, 2)).hasStatus(HttpStatus.CONFLICT);
		assertThat(client.add(breakfast, CHECK_IN, CHECK_OUT, 1, 2)).hasStatusOk();
		// Other dates are another booking, with the rooms of their own nights.
		assertThat(client.add(breakfast, CHECK_OUT, CHECK_OUT.plusDays(1), 3, 2)).hasStatusOk();
		assertThat(client.items()).extracting(line -> line.get("quantity")).containsExactly(2, 1, 3);
	}

	@Test
	void aFullCartTakesNoNewLineButStillAddsToItsLines() {
		long plan = plan(catalog.hotel("Full " + suffix).create(), 5);
		for (int day = 0; day < 20; day++) {
			assertThat(client.add(plan, CHECK_IN.plusDays(day), CHECK_IN.plusDays(day + 1), 1, 1)).hasStatusOk();
		}

		assertThat(client.add(plan, CHECK_IN.plusDays(20), CHECK_IN.plusDays(21), 1, 1)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("The cart can hold at most 20 room choices");
		assertThat(client.add(plan, CHECK_IN, CHECK_IN.plusDays(1), 1, 1)).hasStatusOk();
		assertThat(client.items()).hasSize(20);
	}

	@Test
	void usersSeeOnlyTheirOwnCart() {
		long plan = plan(catalog.hotel("Mine " + suffix).create(), 5);
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2);

		CartClient other = CartClient.signUp(mvc, catalog);

		assertThat(other.cart()).hasStatusOk().bodyJson().extractingPath("$.groups").asArray().isEmpty();
		assertThat(client.items()).hasSize(1);
	}

	@Test
	void needsASignedInUser() {
		assertThat(mvc.get().uri("/api/cart")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/cart/items").contentType("application/json").content(item(1, CHECK_IN,
				CHECK_OUT, 1, 2))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	/** The default rate plan of a room type for 2 adults and up to 2 children with {@code rooms} rooms. */
	private long plan(CreatedHotel hotel, int rooms) {
		return catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, rooms));
	}

}
