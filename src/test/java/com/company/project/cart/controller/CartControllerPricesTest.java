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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The cart is priced again on every read (C-CART-02): changed prices are flagged, lines that can no longer be booked
 * are marked, and a line costs what the hotel page asks for the same rate and stay.
 */
@IntegrationTest
class CartControllerPricesTest {

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
	void aPriceChangedSinceAddingShowsTheOldAndTheNewPrice() {
		long plan = plan(catalog.hotel("Repriced " + suffix).create(), 5);
		client.add(plan, CHECK_IN, CHECK_OUT, 2, 2);

		catalog.nightPrice(plan, CHECK_IN, 1_500_000, null, false);

		Map<String, Object> line = client.items().getFirst();
		assertThat(line).containsEntry("status", "AVAILABLE").containsEntry("priceChanged", true);
		assertThat(money(line.get("priceAtAdd"))).isEqualTo(2 * DEFAULT_PRICE);
		assertThat(money(line.get("pricePerRoom"))).isEqualTo(1_500_000 + DEFAULT_PRICE);
		assertThat(money(line.get("totalPrice"))).isEqualTo(2 * (1_500_000 + DEFAULT_PRICE));
	}

	@Test
	void fewerRoomsLeftThanTheGroupNeedsIsNotEnoughRooms() {
		CreatedHotel hotel = catalog.hotel("Fewer " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		long roomOnly = catalog.ratePlan(room).create();
		long breakfast = catalog.ratePlan(room).board("BB").create();
		client.add(roomOnly, CHECK_IN, CHECK_OUT, 2, 2);
		client.add(breakfast, CHECK_IN, CHECK_OUT, 1, 2);

		catalog.inventory(room, CHECK_IN.plusDays(1), 3, 1, false);

		MvcTestResult result = client.cart();
		assertThat(JsonPath.<List<String>>read(body(result), "$.groups[0].items[*].status"))
			.containsExactly("NOT_ENOUGH_ROOMS", "NOT_ENOUGH_ROOMS");
		assertThat(JsonPath.<List<Integer>>read(body(result), "$.groups[0].items[*].roomsLeft")).containsExactly(2, 2);
		assertThat(JsonPath.<Boolean>read(body(result), "$.groups[0].canCheckout")).isFalse();
		assertThat(JsonPath.<Object>read(body(result), "$.groups[0].total")).isNull();
		// Still priced: the client can offer to lower the quantity.
		assertThat(money(JsonPath.read(body(result), "$.groups[0].items[0].pricePerRoom"))).isEqualTo(2 * DEFAULT_PRICE);
	}

	@Test
	void aLineThatCanNoLongerBeSoldIsUnavailable() {
		CreatedHotel closedHotel = catalog.hotel("A closed " + suffix).create();
		long closed = plan(closedHotel, 5);
		CreatedHotel inactiveHotel = catalog.hotel("B inactive " + suffix).create();
		long inactive = plan(inactiveHotel, 5);
		CreatedHotel unlistedHotel = catalog.hotel("C unlisted " + suffix).create();
		long unlisted = plan(unlistedHotel, 5);
		client.add(closed, CHECK_IN, CHECK_OUT, 1, 2);
		client.add(inactive, CHECK_IN, CHECK_OUT, 1, 2);
		client.add(unlisted, CHECK_IN, CHECK_OUT, 1, 2);

		catalog.nightPrice(closed, CHECK_IN, DEFAULT_PRICE, null, true);
		jdbc.sql("update rate_plans set is_active = false where id = :id").param("id", inactive).update();
		catalog.hotelStatus(unlistedHotel.id(), "INACTIVE");

		MvcTestResult result = client.cart();
		assertThat(JsonPath.<List<String>>read(body(result), "$.groups[*].hotel.slug"))
			.containsExactly(closedHotel.slug(), inactiveHotel.slug(), unlistedHotel.slug());
		assertThat(JsonPath.<List<Boolean>>read(body(result), "$.groups[*].hotel.listed")).containsExactly(true, true,
				false);
		assertThat(JsonPath.<List<Boolean>>read(body(result), "$.groups[*].canCheckout")).containsExactly(false, false,
				false);
		for (Map<String, Object> line : client.items()) {
			assertThat(line).containsEntry("status", "UNAVAILABLE")
				.containsEntry("pricePerRoom", null)
				.containsEntry("totalPrice", null)
				.containsEntry("roomsLeft", null)
				.containsEntry("cancellation", null)
				.containsEntry("priceChanged", false)
				.containsEntry("boardType", "RO");
			assertThat(money(line.get("priceAtAdd"))).isEqualTo(2 * DEFAULT_PRICE);
		}
	}

	@Test
	void aLineWhoseCheckInPassedIsReportedAsSuch() {
		long plan = plan(catalog.hotel("Passed " + suffix).create(), 5);
		catalog.cartItem(client.userId, plan, LocalDate.now().minusDays(3), LocalDate.now().minusDays(1), 1, 2);

		Map<String, Object> line = client.items().getFirst();

		assertThat(line).containsEntry("status", "CHECK_IN_PASSED").containsEntry("pricePerRoom", null);
		assertThat(JsonPath.<Boolean>read(body(client.cart()), "$.groups[0].canCheckout")).isFalse();
	}

	@Test
	void aLineCostsWhatTheHotelPageAsksForTheSameRateAndStay() {
		CreatedHotel hotel = catalog.hotel("Same price " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 1, 3, 4);
		long plan = catalog.ratePlan(room).board("BB").price(850_000).create();
		catalog.rateRule(plan, CHECK_IN, CHECK_IN, CHECK_IN.getDayOfWeek().getValue()).percent("20").create();
		catalog.nightPrice(plan, CHECK_IN.plusDays(1), 990_000, null, false);
		client.add(plan, CHECK_IN, CHECK_OUT, 1, 2, 6);

		MvcTestResult page = mvc.get()
			.uri("/api/hotels/%s/rooms?checkIn=%s&checkOut=%s&rooms=1&adults=2&childAges=6".formatted(hotel.slug(),
					CHECK_IN, CHECK_OUT))
			.exchange();

		assertThat(page).hasStatusOk();
		Map<String, Object> rate = JsonPath.read(body(page), "$.roomTypes[0].rates[0]");
		Map<String, Object> line = client.items().getFirst();
		assertThat(money(line.get("pricePerRoom"))).isEqualTo(money(rate.get("totalPrice")))
			.isEqualTo(1_020_000 + 990_000);
		assertThat(line.get("cancellation")).isEqualTo(rate.get("cancellation"));
		assertThat(line).containsEntry("ratePlanName", rate.get("name"))
			.containsEntry("boardTypeName", rate.get("boardTypeName"));
	}

	@Test
	void theGroupTotalCountsOnlyTheAvailableLines() {
		CreatedHotel hotel = catalog.hotel("Total " + suffix).create();
		long open = plan(hotel, 5);
		long closing = plan(hotel, 5);
		client.add(open, CHECK_IN, CHECK_OUT, 2, 2);
		client.add(closing, CHECK_IN, CHECK_OUT, 1, 2);

		catalog.nightPrice(closing, CHECK_IN, DEFAULT_PRICE, null, true);

		MvcTestResult result = client.cart();
		assertThat(JsonPath.<List<String>>read(body(result), "$.groups[0].items[*].status"))
			.containsExactly("AVAILABLE", "UNAVAILABLE");
		assertThat(money(JsonPath.read(body(result), "$.groups[0].total"))).isEqualTo(2 * 2 * DEFAULT_PRICE);
		assertThat(JsonPath.<Boolean>read(body(result), "$.groups[0].canCheckout")).isFalse();
	}

	/** The default rate plan of a room type for 2 adults and up to 2 children with {@code rooms} rooms. */
	private long plan(CreatedHotel hotel, int rooms) {
		return catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, rooms));
	}

}
