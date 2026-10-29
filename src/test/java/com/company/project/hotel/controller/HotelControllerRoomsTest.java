package com.company.project.hotel.controller;

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
 * The hotel page's room list (C-HOTEL-04) and each rate's cancellation terms (C-HOTEL-06). Every test builds its own
 * hotel and opens it by slug, so no other test's data shows up.
 */
@IntegrationTest
class HotelControllerRoomsTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
	}

	@Test
	void listsEveryRateThatCanSellTheStayCheapestFirst() {
		CreatedHotel hotel = catalog.hotel("Rates " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		long roomOnly = catalog.ratePlan(room).name("Room only " + suffix).price(900_000).create();
		long breakfast = catalog.ratePlan(room).name("Breakfast " + suffix).board("BB").price(800_000).create();
		catalog.ratePlan(room).price(100_000).inactive().create();
		catalog.ratePlan(room).price(1).currency("USD").create();
		long closed = catalog.ratePlan(room).price(500_000).create();
		catalog.nightPrice(closed, CHECK_IN.plusDays(1), 500_000, null, true);
		catalog.ratePlan(room).price(700_000).minNights(3).create();

		MvcTestResult result = rooms(hotel, stay() + "&rooms=1&adults=2");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.nights").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("VND");
		assertThat(ids(result, "$.roomTypes[*].roomTypeId")).containsExactly(room);
		assertThat(ids(result, "$.roomTypes[0].rates[*].ratePlanId")).containsExactly(breakfast, roomOnly);
		Map<String, Object> cheapest = JsonPath.read(body(result), "$.roomTypes[0].rates[0]");
		assertThat(cheapest).containsEntry("name", "Breakfast " + suffix)
			.containsEntry("boardType", "BB")
			.containsEntry("boardTypeName", "Breakfast included");
		assertThat(money(cheapest.get("totalPrice"))).isEqualTo(1_600_000);
		assertThat(money(cheapest.get("pricePerNight"))).isEqualTo(800_000);
	}

	@Test
	void roomsLeftIsTheFewestFreeOnAnyNightAndSoldOutRoomTypesAreHidden() {
		CreatedHotel hotel = catalog.hotel("Rooms left " + suffix).create();
		long fewLeft = catalog.roomType(hotel.id(), 2, 0, 2, 5);
		catalog.ratePlan(fewLeft).board("BB").create();
		catalog.inventory(fewLeft, CHECK_IN.plusDays(1), 5, 3, false);
		long soldOut = catalog.roomType(hotel.id(), 2, 0, 2, 4);
		catalog.inventory(soldOut, CHECK_IN, 4, 4, false);
		long closed = catalog.roomType(hotel.id(), 2, 0, 2, 4);
		catalog.inventory(closed, CHECK_IN.plusDays(1), 4, 0, true);
		long inactive = catalog.roomType(hotel.id(), 2, 0, 2, 4);
		catalog.roomTypeStatus(inactive, "INACTIVE");
		long deleted = catalog.roomType(hotel.id(), 2, 0, 2, 4);
		jdbc.sql("update room_types set deleted_at = now() where id = :id").param("id", deleted).update();

		MvcTestResult result = rooms(hotel, stay() + "&rooms=1&adults=2");

		assertThat(ids(result, "$.roomTypes[*].roomTypeId")).containsExactly(fewLeft);
		assertThat(result).bodyJson().extractingPath("$.roomTypes[0].roomsLeft").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.roomTypes[0].rates").asArray().hasSize(2);
	}

	@Test
	void roomTypesThatCannotHoldTheGuestsComeLastAndAreMarked() {
		CreatedHotel hotel = catalog.hotel("Fit " + suffix).create();
		long small = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		catalog.ratePlan(small).price(300_000).create();
		long large = catalog.bareRoomType(hotel.id(), 3, 0, 3, 3);
		catalog.ratePlan(large).price(900_000).create();

		MvcTestResult result = rooms(hotel, stay() + "&rooms=1&adults=3");

		assertThat(ids(result, "$.roomTypes[*].roomTypeId")).containsExactly(large, small);
		List<Boolean> fits = JsonPath.read(body(result), "$.roomTypes[*].fitsGuests");
		assertThat(fits).containsExactly(true, false);
		assertThat(result).bodyJson().extractingPath("$.roomTypes[1].maxAdults").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.roomTypes[1].maxOccupancy").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.adults").isEqualTo(3);
	}

	@Test
	void fitUsesTheEvenSplitAndLetsChildrenUseFreeAdultPlaces() {
		CreatedHotel hotel = catalog.hotel("Split " + suffix).create();
		long triple = catalog.roomType(hotel.id(), 3, 0, 3, 3);
		long twin = catalog.roomType(hotel.id(), 2, 0, 2, 3);

		// 2 adults and a child: the child takes the third adult place of the triple.
		MvcTestResult family = rooms(hotel, stay() + "&rooms=1&adults=2&childAges=7");
		assertThat(fitsByRoomType(family)).containsExactly(Map.entry(triple, true), Map.entry(twin, false));
		assertThat(family).bodyJson().extractingPath("$.children").isEqualTo(1);
		// 3 adults in 2 rooms need rooms for 2 adults each.
		MvcTestResult group = rooms(hotel, stay() + "&rooms=2&adults=3");
		assertThat(fitsByRoomType(group)).containsOnly(Map.entry(triple, true), Map.entry(twin, true));
	}

	@Test
	void describesEachRoomType() {
		CreatedHotel hotel = catalog.hotel("Details " + suffix).create();
		long room = catalog.roomType(hotel.id(), 2, 1, 3, 3);
		catalog.roomTypeDetails(room, "Corner room " + suffix, "28.5", 1);
		catalog.bed(room, "king", 1);
		catalog.bed(room, "single", 2);
		catalog.roomAmenity(room, "sea_view");
		catalog.roomAmenity(room, "balcony");
		// A hotel facility linked to the room type is not a room feature.
		catalog.roomAmenity(room, "wifi");
		catalog.roomPhoto(hotel.id(), room, "https://img.test/second-" + suffix, null, 2);
		catalog.roomPhoto(hotel.id(), room, "https://img.test/first-" + suffix, "room", 1);

		MvcTestResult result = rooms(hotel, stay() + "&rooms=1&adults=2");

		Map<String, Object> option = JsonPath.read(body(result), "$.roomTypes[0]");
		assertThat(option).containsEntry("description", "Corner room " + suffix)
			.containsEntry("sizeSqm", 28.5)
			.containsEntry("maxAdults", 2)
			.containsEntry("maxChildren", 1)
			.containsEntry("maxOccupancy", 3)
			.containsEntry("fitsGuests", true)
			.containsEntry("roomsLeft", 3);
		assertThat(option.get("name")).asString().startsWith("Room ");
		List<String> beds = JsonPath.read(body(result), "$.roomTypes[0].beds[*].code");
		assertThat(beds).containsExactly("single", "king");
		assertThat(result).bodyJson().extractingPath("$.roomTypes[0].beds[0].quantity").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.roomTypes[0].beds[0].name").isEqualTo("Single bed");
		List<String> features = JsonPath.read(body(result), "$.roomTypes[0].features[*].code");
		assertThat(features).containsExactly("balcony", "sea_view");
		assertThat(result).bodyJson()
			.extractingPath("$.roomTypes[0].photoUrls")
			.isEqualTo(List.of("https://img.test/first-" + suffix, "https://img.test/second-" + suffix));
	}

	@Test
	void pricesFollowTheNightPriceAndThePerNightAverageRoundsHalfUp() {
		CreatedHotel hotel = catalog.hotel("Prices " + suffix).create();
		long plan = catalog.ratePlan(catalog.bareRoomType(hotel.id(), 2, 0, 2, 1)).create();
		LocalDate first = CHECK_IN;
		catalog.nightPrice(plan, first.plusDays(1), 1_000_001, null, false);
		catalog.rateRule(plan, first.plusDays(2), first.plusDays(2)).percent("10").create();

		MvcTestResult result = rooms(hotel, "checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(first, first.plusDays(3)));

		Map<String, Object> rate = JsonPath.read(body(result), "$.roomTypes[0].rates[0]");
		assertThat(money(rate.get("totalPrice"))).isEqualTo(1_000_000 + 1_000_001 + 1_100_000);
		// 3,100,001 / 3 = 1,033,333.67
		assertThat(money(rate.get("pricePerNight"))).isEqualTo(1_033_334);
	}

	@Test
	void taxesIncludedFollowsTheCountrysRules() {
		CreatedHotel taxed = catalog.hotel("Taxed " + suffix).country("XR").create();
		catalog.roomType(taxed.id(), 2, 0, 2, 1);
		catalog.taxRule("XR", false, null, null);
		CreatedHotel untaxed = catalog.hotel("Untaxed " + suffix).country("XS").create();
		catalog.roomType(untaxed.id(), 2, 0, 2, 1);

		assertThat(rooms(taxed, stay() + "&rooms=1&adults=2")).bodyJson()
			.extractingPath("$.taxesIncluded")
			.isEqualTo(false);
		assertThat(rooms(untaxed, stay() + "&rooms=1&adults=2")).bodyJson()
			.extractingPath("$.taxesIncluded")
			.isEqualTo(true);
	}

	@Test
	void theCheapestFittingRateIsTheSearchCardsOffer() {
		CreatedHotel hotel = catalog.hotel("Same offer " + suffix).create();
		// Cheapest overall, but too small for two adults: the search skips it, the room list puts it last.
		long single = catalog.bareRoomType(hotel.id(), 1, 0, 1, 3);
		catalog.ratePlan(single).price(200_000).create();
		long tiered = tieredPolicy();
		long standard = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		catalog.ratePlan(standard).price(950_000).create();
		catalog.ratePlan(standard).price(850_000).policy(tiered).create();
		long superior = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		catalog.ratePlan(superior).price(900_000).create();

		MvcTestResult search = mvc.get()
			.uri("/api/search/hotels?hotel=%s&%s&rooms=1&adults=2".formatted(hotel.slug(), stay()))
			.exchange();
		MvcTestResult list = rooms(hotel, stay() + "&rooms=1&adults=2");

		Map<String, Object> line = JsonPath.read(body(search), "$.content[0].offer.roomLines[0]");
		Map<String, Object> rate = JsonPath.read(body(list), "$.roomTypes[0].rates[0]");
		assertThat(ids(list, "$.roomTypes[0].roomTypeId")).containsExactly(((Number) line.get("roomTypeId")).longValue());
		assertThat(((Number) rate.get("ratePlanId")).longValue()).isEqualTo(((Number) line.get("ratePlanId")).longValue());
		assertThat(money(rate.get("totalPrice"))).isEqualTo(money(line.get("price"))).isEqualTo(1_700_000);
		assertThat(((Map<?, ?>) rate.get("cancellation")).get("freeUntil"))
			.isEqualTo(((Map<?, ?>) line.get("cancellation")).get("freeUntil"))
			.isEqualTo(CHECK_IN.minusDays(3) + "T14:00:00+07:00");
		assertThat(ids(list, "$.roomTypes[*].roomTypeId")).containsExactly(standard, superior, single);
	}

	@Test
	void eachRateListsItsPenaltiesAfterFreeCancellation() {
		CreatedHotel hotel = catalog.hotel("Penalties " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		catalog.ratePlan(room).price(1_000_000).policy(tieredPolicy()).create();

		MvcTestResult result = rooms(hotel, stay() + "&rooms=1&adults=2");

		Map<String, Object> cancellation = JsonPath.read(body(result), "$.roomTypes[0].rates[0].cancellation");
		assertThat(cancellation).containsEntry("refundable", true)
			.containsEntry("policyName", "Tiered " + suffix)
			.containsEntry("freeUntil", CHECK_IN.minusDays(3) + "T14:00:00+07:00");
		List<Map<String, Object>> penalties = JsonPath.read(body(result), "$.roomTypes[0].rates[0].cancellation.penalties");
		assertThat(penalties).hasSize(2);
		assertThat(penalties.get(0)).containsEntry("from", CHECK_IN.minusDays(3) + "T14:00:00+07:00")
			.containsEntry("until", CHECK_IN.minusDays(1) + "T14:00:00+07:00");
		assertThat(money(penalties.get(0).get("amount"))).isEqualTo(1_000_000);
		assertThat(penalties.get(1)).containsEntry("from", CHECK_IN.minusDays(1) + "T14:00:00+07:00")
			.containsEntry("until", null);
		assertThat(money(penalties.get(1).get("amount"))).isEqualTo(2_000_000);
	}

	@Test
	void penaltiesCanBeANonRefundableStayNightsOrAFixedAmount() {
		CreatedHotel hotel = catalog.hotel("Penalty kinds " + suffix).create();
		long room = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		long nonRefundable = catalog.cancellationPolicy("Non-refundable " + suffix, false);
		catalog.cancellationRule(nonRefundable, 100_000, "PERCENT", "100");
		catalog.ratePlan(room).price(500_000).policy(nonRefundable).create();
		long oneNight = catalog.cancellationPolicy("One night " + suffix, true);
		catalog.cancellationRule(oneNight, 48, "NIGHTS", "1");
		catalog.ratePlan(room).price(600_000).policy(oneNight).create();
		long fixed = catalog.cancellationPolicy("Fixed fee " + suffix, true);
		catalog.cancellationRule(fixed, 24, "FIXED", "250000");
		catalog.ratePlan(room).price(700_000).policy(fixed).create();
		// Without rules a refundable rate is free until check-in.
		catalog.ratePlan(room).price(800_000).create();

		MvcTestResult result = rooms(hotel, stay() + "&rooms=1&adults=2");

		List<Map<String, Object>> rates = JsonPath.read(body(result), "$.roomTypes[0].rates");
		assertThat(cancellation(rates.get(0))).containsEntry("refundable", false).containsEntry("freeUntil", null);
		assertThat(penalties(rates.get(0))).singleElement().satisfies(penalty -> {
			assertThat(penalty).containsEntry("from", null).containsEntry("until", null);
			assertThat(money(penalty.get("amount"))).isEqualTo(1_000_000);
		});
		assertThat(penalties(rates.get(1))).singleElement().satisfies(penalty -> {
			assertThat(penalty).containsEntry("from", CHECK_IN.minusDays(2) + "T14:00:00+07:00");
			assertThat(money(penalty.get("amount"))).isEqualTo(600_000);
		});
		assertThat(penalties(rates.get(2))).singleElement()
			.satisfies(penalty -> assertThat(money(penalty.get("amount"))).isEqualTo(250_000));
		assertThat(cancellation(rates.get(3))).containsEntry("freeUntil", CHECK_IN + "T14:00:00+07:00");
		assertThat(penalties(rates.get(3))).isEmpty();
	}

	@Test
	void rejectsStaysTheSearchWouldReject() {
		CreatedHotel hotel = catalog.hotel("Invalid " + suffix).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 1);
		LocalDate yesterday = LocalDate.now().minusDays(2);

		assertThat(rooms(hotel, "checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(yesterday, CHECK_IN)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rooms(hotel, "checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(CHECK_IN, CHECK_IN)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rooms(hotel, stay() + "&rooms=9&adults=9")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rooms(hotel, stay() + "&rooms=2&adults=1")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rooms(hotel, "checkOut=%s&rooms=1&adults=2".formatted(CHECK_OUT))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(rooms(hotel, stay() + "&rooms=1&adults=2&childAges=18")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void onlyListedHotelsHaveARoomList() {
		CreatedHotel inactive = catalog.hotel("Inactive " + suffix).status("INACTIVE").create();
		catalog.roomType(inactive.id(), 2, 0, 2, 1);

		assertThat(mvc.get().uri("/api/hotels/no-such-hotel-%s/rooms?%s&rooms=1&adults=2".formatted(suffix, stay())))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(rooms(inactive, stay() + "&rooms=1&adults=2")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void aHotelWithNothingToSellHasAnEmptyList() {
		CreatedHotel hotel = catalog.hotel("Nothing " + suffix).create();
		catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);

		MvcTestResult result = rooms(hotel, stay() + "&rooms=1&adults=2");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.roomTypes").asArray().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.taxesIncluded").isEqualTo(true);
	}

	// Free until 72 hours before check-in, half the stay from then, all of it in the last 24 hours.
	private long tieredPolicy() {
		long policy = catalog.cancellationPolicy("Tiered " + suffix, true);
		catalog.cancellationRule(policy, 72, "PERCENT", "50");
		catalog.cancellationRule(policy, 24, "PERCENT", "100");
		return policy;
	}

	private MvcTestResult rooms(CreatedHotel hotel, String query) {
		return mvc.get().uri("/api/hotels/%s/rooms?%s".formatted(hotel.slug(), query)).exchange();
	}

	private static String stay() {
		return "checkIn=%s&checkOut=%s".formatted(CHECK_IN, CHECK_OUT);
	}

	private static List<Long> ids(MvcTestResult result, String path) {
		assertThat(result).hasStatusOk();
		Object read = JsonPath.read(body(result), path);
		List<?> values = read instanceof List<?> list ? list : List.of(read);
		return values.stream().map(value -> ((Number) value).longValue()).toList();
	}

	private static List<Map.Entry<Long, Boolean>> fitsByRoomType(MvcTestResult result) {
		List<Map<String, Object>> options = JsonPath.read(body(result), "$.roomTypes");
		return options.stream()
			.map(option -> Map.entry(((Number) option.get("roomTypeId")).longValue(), (Boolean) option.get("fitsGuests")))
			.toList();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cancellation(Map<String, Object> rate) {
		return (Map<String, Object>) rate.get("cancellation");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> penalties(Map<String, Object> rate) {
		return (List<Map<String, Object>>) cancellation(rate).get("penalties");
	}

	private static long money(Object money) {
		return ((Number) ((Map<?, ?>) money).get("amount")).longValue();
	}

}
