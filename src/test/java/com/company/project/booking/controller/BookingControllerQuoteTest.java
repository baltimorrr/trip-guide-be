package com.company.project.booking.controller;

import static com.company.project.booking.controller.BookingClient.money;
import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The checkout quote (C-BOOK-01/02/05): one cart group priced like the cart, with taxes by type, the total and the
 * booker pre-filled from the profile. Tax rules apply to a whole country, so each test uses its own test country.
 */
@IntegrationTest
class BookingControllerQuoteTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	BookingClient client;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		client = BookingClient.signUp(mvc, catalog);
	}

	@Test
	void aGroupIsQuotedLineByLineWithItsTaxesAndTotal() {
		CreatedHotel hotel = catalog.hotel("Quoted " + suffix).country("XD").create();
		catalog.taxRule("XD", "VAT", "PERCENT", "10", null, false, true);
		long standard = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, 5));
		long deluxe = catalog.ratePlan(catalog.bareRoomType(hotel.id(), 2, 1, 3, 3)).board("BB").price(1_500_000)
			.create();
		long standardLine = client.addToCart(standard, CHECK_IN, CHECK_OUT, 2, 2);
		long deluxeLine = client.addToCart(deluxe, CHECK_IN, CHECK_OUT, 1, 2, 7);

		MvcTestResult quote = client.quote(null, standardLine, deluxeLine);

		assertThat(quote).hasStatusOk();
		assertThat(JsonPath.<String>read(body(quote), "$.hotel.slug")).isEqualTo(hotel.slug());
		assertThat(JsonPath.<Integer>read(body(quote), "$.nights")).isEqualTo(2);
		List<Map<String, Object>> lines = JsonPath.read(body(quote), "$.lines");
		assertThat(lines).extracting(line -> line.get("cartItemId"), line -> line.get("quantity"),
				line -> money(line.get("pricePerRoom")), line -> money(line.get("subtotal")))
			.containsExactly(
					tuple((int) standardLine, 2, 2 * DEFAULT_PRICE, 4 * DEFAULT_PRICE),
					tuple((int) deluxeLine, 1, 3_000_000L, 3_000_000L));
		assertThat(lines.get(1)).containsEntry("boardType", "BB").containsEntry("childAges", List.of(7));
		assertThat(lines.get(0).get("cancellation")).isNotNull();
		assertThat(money(JsonPath.read(body(quote), "$.price.subtotal"))).isEqualTo(7_000_000);
		assertThat(JsonPath.<Object>read(body(quote), "$.price.discount")).isNull();
		assertThat(JsonPath.<List<Map<String, Object>>>read(body(quote), "$.price.taxes"))
			.singleElement()
			.satisfies(tax -> {
				assertThat(tax).containsEntry("name", "VAT").containsEntry("included", false);
				assertThat(money(tax.get("amount"))).isEqualTo(700_000);
			});
		assertThat(money(JsonPath.read(body(quote), "$.price.total"))).isEqualTo(7_700_000);
		assertThat(JsonPath.<String>read(body(quote), "$.price.total.currency")).isEqualTo("VND");
	}

	@Test
	void aLineCostsWhatTheCartShowsForIt() {
		CreatedHotel hotel = catalog.hotel("Same as cart " + suffix).create();
		long plan = catalog.ratePlan(catalog.bareRoomType(hotel.id(), 2, 1, 3, 4)).price(850_000).create();
		catalog.rateRule(plan, CHECK_IN, CHECK_IN, CHECK_IN.getDayOfWeek().getValue()).percent("20").create();
		long line = client.addToCart(plan, CHECK_IN, CHECK_OUT, 2, 2, 6);

		MvcTestResult quote = client.quote(null, line);
		MvcTestResult cart = client.cart();

		Map<String, Object> cartLine = JsonPath.read(body(cart), "$.groups[0].items[0]");
		Map<String, Object> quoteLine = JsonPath.read(body(quote), "$.lines[0]");
		assertThat(money(quoteLine.get("pricePerRoom"))).isEqualTo(money(cartLine.get("pricePerRoom")))
			.isEqualTo(1_020_000 + 850_000);
		assertThat(money(quoteLine.get("subtotal"))).isEqualTo(money(cartLine.get("totalPrice")));
		assertThat(quoteLine.get("cancellation")).isEqualTo(cartLine.get("cancellation"));
		assertThat(money(JsonPath.read(body(quote), "$.price.total")))
			.isEqualTo(money(JsonPath.read(body(cart), "$.groups[0].total")));
	}

	@Test
	void anIncludedTaxIsListedButNotAdded() {
		CreatedHotel hotel = catalog.hotel("Included " + suffix).country("XE").create();
		catalog.taxRule("XE", "VAT", "PERCENT", "8", null, true, true);
		catalog.taxRule("XE", "City fee", "PER_PERSON_NIGHT", "10000", "VND", false, true);
		long line = client.addToCart(catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, 5)), CHECK_IN,
				CHECK_OUT, 1, 2, 5);

		MvcTestResult quote = client.quote(null, line);

		assertThat(quote).hasStatusOk();
		assertThat(JsonPath.<List<Boolean>>read(body(quote), "$.price.taxes[*].included")).containsExactly(true, false);
		// 8% inside 2,000,000; 3 guests × 2 nights × 10,000
		assertThat(JsonPath.<List<Map<String, Object>>>read(body(quote), "$.price.taxes[*].amount"))
			.extracting(BookingClient::money).containsExactly(148_148L, 60_000L);
		assertThat(money(JsonPath.read(body(quote), "$.price.total"))).isEqualTo(2 * DEFAULT_PRICE + 60_000);
	}

	@Test
	void linesOfTwoHotelsOrTwoDateRangesAreNotOneBooking() {
		CreatedHotel hotel = catalog.hotel("One group " + suffix).create();
		long plan = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, 5));
		long otherPlan = catalog.defaultRatePlan(
				catalog.roomType(catalog.hotel("Other group " + suffix).create().id(), 2, 2, 4, 5));
		long first = client.addToCart(plan, CHECK_IN, CHECK_OUT, 1, 2);
		long otherDates = client.addToCart(plan, CHECK_OUT, CHECK_OUT.plusDays(1), 1, 2);
		long otherHotel = client.addToCart(otherPlan, CHECK_IN, CHECK_OUT, 1, 2);

		for (MvcTestResult quote : List.of(client.quote(null, first, otherDates), client.quote(null, first, otherHotel))) {
			assertThat(quote).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(JsonPath.<String>read(body(quote), "$.detail"))
				.isEqualTo("A booking holds the rooms of one hotel for one date range");
		}
	}

	@Test
	void onlyTheUsersOwnLinesCanBeQuoted() {
		long plan = catalog.defaultRatePlan(catalog.roomType(catalog.hotel("Own " + suffix).create().id(), 2, 2, 4, 5));
		long mine = client.addToCart(plan, CHECK_IN, CHECK_OUT, 1, 2);
		long theirs = BookingClient.signUp(mvc, catalog).addToCart(plan, CHECK_IN, CHECK_OUT, 1, 2);

		assertThat(client.quote(null, mine, theirs)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(client.quote(null, Long.MAX_VALUE)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void aLineThatCanNoLongerBeBookedRefusesTheQuote() {
		CreatedHotel hotel = catalog.hotel("Gone " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 0, 2, 5);
		long plan = catalog.ratePlan(room).name("Closing rate").create();
		long line = client.addToCart(plan, CHECK_IN, CHECK_OUT, 1, 2);
		catalog.nightPrice(plan, CHECK_IN.plusDays(1), DEFAULT_PRICE, null, true);

		MvcTestResult closed = client.quote(null, line);

		assertThat(closed).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(closed), "$.detail")).endsWith("is no longer available for these dates");

		long past = catalog.cartItem(client.userId, catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 0, 2, 5)),
				LocalDate.now().minusDays(3), LocalDate.now().minusDays(1), 1, 2);
		MvcTestResult passed = client.quote(null, past);
		assertThat(passed).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(passed), "$.detail")).isEqualTo("Check-in for these rooms has passed");
	}

	@Test
	void theGroupsLinesOfOneRoomTypeShareTheRoomsLeft() {
		CreatedHotel hotel = catalog.hotel("Shared rooms " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		long roomOnly = catalog.ratePlan(room).create();
		long breakfast = catalog.ratePlan(room).board("BB").create();
		long first = client.addToCart(roomOnly, CHECK_IN, CHECK_OUT, 2, 2);
		long second = client.addToCart(breakfast, CHECK_IN, CHECK_OUT, 1, 2);
		catalog.inventory(room, CHECK_IN, 3, 1, false);

		MvcTestResult quote = client.quote(null, first, second);

		assertThat(quote).hasStatus(HttpStatus.CONFLICT);
		assertThat(JsonPath.<String>read(body(quote), "$.detail")).startsWith("Only 2 rooms of ")
			.endsWith(" are left for these dates");
		assertThat(client.quote(null, first)).hasStatusOk();
	}

	@Test
	void theBookerIsPrefilledFromTheProfile() {
		long line = client.addToCart(
				catalog.defaultRatePlan(catalog.roomType(catalog.hotel("Prefill " + suffix).create().id(), 2, 2, 4, 5)),
				CHECK_IN, CHECK_OUT, 1, 2);
		MvcTestResult before = client.quote(null, line);
		client.profile("Lan Nguyen", "+84901234567", "vn");

		MvcTestResult after = client.quote(null, line);

		assertThat(JsonPath.<Map<String, Object>>read(body(before), "$.booker")).containsEntry("email", client.email)
			.containsEntry("phone", null)
			.containsEntry("countryCode", null);
		assertThat(JsonPath.<Map<String, Object>>read(body(after), "$.booker"))
			.containsExactlyInAnyOrderEntriesOf(Map.of("fullName", "Lan Nguyen", "email", client.email, "phone",
					"+84901234567", "countryCode", "VN"));
	}

	@Test
	void aQuoteNeedsASignedInUserAndAtLeastOneLine() {
		assertThat(mvc.post().uri("/api/bookings/quote").contentType(MediaType.APPLICATION_JSON)
			.content("{\"cartItemIds\": [1]}")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(client.quote(null)).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
