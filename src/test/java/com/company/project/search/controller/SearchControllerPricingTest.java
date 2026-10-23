package com.company.project.search.controller;

import static com.company.project.support.CatalogFixtures.DEFAULT_PRICE;
import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.company.project.destination.services.DestinationSyncService;
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
 * The result list's prices and terms (C-SRCH-01): each hotel's cheapest offer for the stay. Hotels are searched
 * by slug unless the test is about the list itself, so no other test's hotels show up.
 */
@IntegrationTest
class SearchControllerPricingTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DestinationSyncService syncService;

	CatalogFixtures catalog;

	String suffix;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
	}

	@Test
	void showsTheCheapestRatePlanOfTheCheapestRoomType() {
		CreatedHotel hotel = catalog.hotel("Cheapest " + suffix).create();
		long standard = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		catalog.ratePlan(standard).price(900_000).create();
		catalog.ratePlan(standard).board("BB").price(800_000).create();
		long superior = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		long halfBoard = catalog.ratePlan(superior).name("Half board " + suffix).board("HB").price(750_000).create();
		catalog.ratePlan(superior).price(950_000).create();

		MvcTestResult result = searchHotel(hotel, stay() + "&rooms=1&adults=2");

		Map<String, Object> offer = offer(result);
		assertThat(offer).containsEntry("nights", 2).containsEntry("rooms", 1).containsEntry("taxesIncluded", true);
		assertThat(money(offer.get("totalPrice"))).isEqualTo(1_500_000);
		assertThat(currency(offer.get("totalPrice"))).isEqualTo("VND");
		List<Map<String, Object>> lines = lines(result);
		assertThat(lines).singleElement().satisfies(line -> {
			assertThat(((Number) line.get("roomTypeId")).longValue()).isEqualTo(superior);
			assertThat(((Number) line.get("ratePlanId")).longValue()).isEqualTo(halfBoard);
			assertThat(line).containsEntry("ratePlanName", "Half board " + suffix)
				.containsEntry("quantity", 1)
				.containsEntry("boardType", "HB")
				.containsEntry("boardTypeName", "Half board");
			assertThat(money(line.get("price"))).isEqualTo(1_500_000);
		});
	}

	@Test
	void nightPriceUsesOverrideThenHighestPriorityRuleThenBase() {
		LocalDate first = CHECK_IN;
		LocalDate second = CHECK_IN.plusDays(1);
		LocalDate third = CHECK_IN.plusDays(2);
		CreatedHotel hotel = catalog.hotel("Seasons " + suffix).create();
		long plan = catalog.ratePlan(catalog.bareRoomType(hotel.id(), 2, 0, 2, 1)).price(100_000).create();
		// Every night: a fixed season price.
		catalog.rateRule(plan, first.minusDays(5), third.plusDays(5)).setPrice(120_000).create();
		// Second and third night's weekdays: +50%, winning over the season price.
		catalog.rateRule(plan, first, third, second.getDayOfWeek().getValue(), third.getDayOfWeek().getValue())
			.percent("50")
			.priority(1)
			.create();
		// Highest priority, but only after the stay.
		catalog.rateRule(plan, third.plusDays(1), third.plusDays(9)).amount(-30_000).priority(5).create();
		// The owner's price for the third night wins over every rule.
		catalog.nightPrice(plan, third, 90_000, null, false);

		MvcTestResult result = searchHotel(hotel, "checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(first,
				third.plusDays(1)));

		assertThat(money(offer(result).get("totalPrice"))).isEqualTo(120_000 + 150_000 + 90_000);

		CreatedHotel discounted = catalog.hotel("Discounted " + suffix).create();
		long discountedPlan = catalog.ratePlan(catalog.bareRoomType(discounted.id(), 2, 0, 2, 1))
			.price(100_000)
			.create();
		catalog.rateRule(discountedPlan, first, second).amount(-30_000).create();
		assertThat(money(offer(searchHotel(discounted, stay() + "&rooms=1&adults=2")).get("totalPrice")))
			.isEqualTo(2 * 70_000);
	}

	@Test
	void closedRateNightOrUnmetMinimumStaySkipsThatRatePlan() {
		CreatedHotel closedNight = catalog.hotel("Closed rate " + suffix).create();
		long closedRoom = catalog.bareRoomType(closedNight.id(), 2, 0, 2, 2);
		long cheapClosed = catalog.ratePlan(closedRoom).price(500_000).create();
		catalog.nightPrice(cheapClosed, CHECK_IN.plusDays(1), 500_000, null, true);
		long dearOpen = catalog.ratePlan(closedRoom).price(800_000).create();

		CreatedHotel minimumStay = catalog.hotel("Minimum stay " + suffix).create();
		long minimumRoom = catalog.bareRoomType(minimumStay.id(), 2, 0, 2, 2);
		catalog.ratePlan(minimumRoom).price(500_000).minNights(3).create();
		long dearShort = catalog.ratePlan(minimumRoom).price(800_000).create();

		// The second night alone asks for 3 nights, so a 2-night stay through it is not sold.
		CreatedHotel onlyLongStays = catalog.hotel("Long stays " + suffix).create();
		long longStayPlan = catalog.ratePlan(catalog.bareRoomType(onlyLongStays.id(), 2, 0, 2, 2)).create();
		catalog.nightPrice(longStayPlan, CHECK_IN.plusDays(1), DEFAULT_PRICE, 3, false);

		assertThat(ratePlanIds(searchHotel(closedNight, stay() + "&rooms=1&adults=2"))).containsExactly(dearOpen);
		assertThat(ratePlanIds(searchHotel(minimumStay, stay() + "&rooms=1&adults=2"))).containsExactly(dearShort);
		assertThat(names(searchHotel(onlyLongStays, stay() + "&rooms=1&adults=2"))).isEmpty();
		assertThat(names(searchHotel(onlyLongStays, "checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(CHECK_IN,
				CHECK_IN.plusDays(3))))).containsExactly(onlyLongStays.name());
	}

	@Test
	void roomTypeWithoutSellableRatePlanDoesNotCount() {
		String city = "Unsellable City " + suffix;
		CreatedHotel sellable = catalog.hotel("Sellable " + suffix).city(city).create();
		catalog.roomType(sellable.id(), 2, 0, 2, 1);
		CreatedHotel noRate = catalog.hotel("No rate " + suffix).city(city).create();
		catalog.bareRoomType(noRate.id(), 2, 0, 2, 1);
		CreatedHotel inactiveRate = catalog.hotel("Inactive rate " + suffix).city(city).create();
		catalog.ratePlan(catalog.bareRoomType(inactiveRate.id(), 2, 0, 2, 1)).inactive().create();
		CreatedHotel otherCurrency = catalog.hotel("Dollar rate " + suffix).city(city).create();
		catalog.ratePlan(catalog.bareRoomType(otherCurrency.id(), 2, 0, 2, 1)).currency("USD").create();
		syncService.sync();

		MvcTestResult result = search("destination=vn-unsellable-city-%s&%s&rooms=1&adults=2".formatted(suffix, stay()));

		assertThat(names(result)).containsExactly(sellable.name());
		assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
	}

	@Test
	void severalRoomsFillFromTheCheapestRoomType() {
		CreatedHotel hotel = catalog.hotel("Mix " + suffix).create();
		long cheap = catalog.bareRoomType(hotel.id(), 2, 0, 2, 1);
		catalog.ratePlan(cheap).price(500_000).create();
		long dear = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		catalog.ratePlan(dear).price(800_000).create();

		MvcTestResult oneRoom = searchHotel(hotel, stay() + "&rooms=1&adults=2");
		assertThat(roomTypeQuantities(oneRoom)).containsExactly(List.of(cheap, 1L));

		MvcTestResult threeRooms = searchHotel(hotel, stay() + "&rooms=3&adults=3");
		assertThat(roomTypeQuantities(threeRooms)).containsExactly(List.of(cheap, 1L), List.of(dear, 2L));
		Map<String, Object> offer = offer(threeRooms);
		assertThat(offer).containsEntry("rooms", 3);
		assertThat(money(offer.get("totalPrice"))).isEqualTo(2 * 500_000 + 2 * 2 * 800_000);
		assertThat(lines(threeRooms).stream().map(line -> money(line.get("price"))))
			.containsExactly(2L * 500_000, 2L * 2 * 800_000);
	}

	@Test
	void taxesIncludedFollowsTheCountrysRules() {
		// Tax rules apply to a whole country: these countries are not used by any other test.
		CreatedHotel taxedOnTop = catalog.hotel("Taxed " + suffix).country("XA").create();
		catalog.roomType(taxedOnTop.id(), 2, 0, 2, 1);
		catalog.taxRule("XA", false, null, null);

		CreatedHotel taxIncluded = catalog.hotel("Included " + suffix).country("XB").create();
		catalog.roomType(taxIncluded.id(), 2, 0, 2, 1);
		catalog.taxRule("XB", true, null, null);
		catalog.taxRule("XB", false, null, CHECK_IN.minusDays(1));

		// Starts on the check-out day, which is not a night of the stay.
		CreatedHotel taxLater = catalog.hotel("Later tax " + suffix).country("XC").create();
		catalog.roomType(taxLater.id(), 2, 0, 2, 1);
		catalog.taxRule("XC", false, CHECK_OUT, null);

		assertThat(offer(searchHotel(taxedOnTop, stay() + "&rooms=1&adults=2"))).containsEntry("taxesIncluded", false);
		assertThat(offer(searchHotel(taxIncluded, stay() + "&rooms=1&adults=2"))).containsEntry("taxesIncluded", true);
		assertThat(offer(searchHotel(taxLater, stay() + "&rooms=1&adults=2"))).containsEntry("taxesIncluded", true);
		assertThat(offer(searchHotel(taxLater, "checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(CHECK_IN,
				CHECK_OUT.plusDays(1))))).containsEntry("taxesIncluded", false);
	}

	@Test
	void freeCancellationDeadlineIsInTheHotelsTimeZone() {
		CreatedHotel flexible = catalog.hotel("Flexible " + suffix).create();
		long policy = catalog.cancellationPolicy("Free until 2 days before " + suffix, true);
		catalog.cancellationRule(policy, 48, "PERCENT", "100");
		catalog.cancellationRule(policy, 168, "PERCENT", "0"); // costs nothing, so it does not end free cancellation
		catalog.ratePlan(catalog.bareRoomType(flexible.id(), 2, 0, 2, 1)).policy(policy).create();

		CreatedHotel strict = catalog.hotel("Strict " + suffix).create();
		long nonRefundable = catalog.cancellationPolicy("Non-refundable " + suffix, false);
		catalog.cancellationRule(nonRefundable, 100_000, "PERCENT", "100");
		catalog.ratePlan(catalog.bareRoomType(strict.id(), 2, 0, 2, 1)).policy(nonRefundable).create();

		CreatedHotel noRules = catalog.hotel("Free " + suffix).create();
		catalog.roomType(noRules.id(), 2, 0, 2, 1);

		assertThat(cancellation(searchHotel(flexible, stay() + "&rooms=1&adults=2")))
			.containsEntry("refundable", true)
			.containsEntry("freeUntil", CHECK_IN.minusDays(2) + "T14:00:00+07:00")
			.containsEntry("policyName", "Free until 2 days before " + suffix);
		assertThat(cancellation(searchHotel(strict, stay() + "&rooms=1&adults=2")))
			.containsEntry("refundable", false)
			.containsEntry("freeUntil", null)
			.containsEntry("policyName", "Non-refundable " + suffix);
		assertThat(cancellation(searchHotel(noRules, stay() + "&rooms=1&adults=2")))
			.containsEntry("freeUntil", CHECK_IN + "T14:00:00+07:00");
	}

	@Test
	void cardListsUpToTenHotelPhotosCoverFirstWithoutRoomPhotos() {
		CreatedHotel hotel = catalog.hotel("Gallery " + suffix).create();
		long room = catalog.roomType(hotel.id(), 2, 0, 2, 1);
		catalog.roomPhoto(hotel.id(), room, "https://img.test/room.jpg");
		for (int i = 1; i <= 11; i++) {
			catalog.photo(hotel.id(), "https://img.test/%02d.jpg".formatted(i), false, i);
		}
		catalog.photo(hotel.id(), "https://img.test/cover.jpg", true, 99);

		List<String> photos = JsonPath.read(body(searchHotel(hotel, stay() + "&rooms=1&adults=2")),
				"$.content[0].photoUrls");

		assertThat(photos).hasSize(10)
			.startsWith("https://img.test/cover.jpg", "https://img.test/01.jpg")
			.endsWith("https://img.test/09.jpg")
			.doesNotContain("https://img.test/room.jpg");
	}

	@Test
	void pagesKeepTheirOrderTotalAndOwnOffers() {
		String city = "Paged City " + suffix;
		CreatedHotel best = catalog.hotel("Best " + suffix).city(city).userRating("9.0", 10).create();
		long bestRoom = catalog.roomType(best.id(), 2, 0, 2, 1);
		CreatedHotel middle = catalog.hotel("Middle " + suffix).city(city).userRating("8.0", 10).create();
		long middleRoom = catalog.roomType(middle.id(), 2, 0, 2, 1);
		CreatedHotel last = catalog.hotel("Last " + suffix).city(city).userRating("7.0", 10).create();
		long lastRoom = catalog.roomType(last.id(), 2, 0, 2, 1);
		syncService.sync();
		String query = "destination=vn-paged-city-%s&%s&rooms=1&adults=2&size=2".formatted(suffix, stay());

		MvcTestResult first = search(query + "&page=0");
		MvcTestResult second = search(query + "&page=1");

		assertThat(names(first)).containsExactly(best.name(), middle.name());
		assertThat(roomTypeIds(first)).containsExactly(bestRoom, middleRoom);
		assertThat(first).bodyJson().extractingPath("$.page.totalElements").isEqualTo(3);
		assertThat(first).bodyJson().extractingPath("$.page.totalPages").isEqualTo(2);
		assertThat(names(second)).containsExactly(last.name());
		assertThat(roomTypeIds(second)).containsExactly(lastRoom);
	}

	private static String stay() {
		return "checkIn=%s&checkOut=%s".formatted(CHECK_IN, CHECK_OUT);
	}

	private MvcTestResult search(String query) {
		return mvc.get().uri("/api/search/hotels?" + query).exchange();
	}

	private MvcTestResult searchHotel(CreatedHotel hotel, String query) {
		return search("hotel=%s&%s".formatted(hotel.slug(), query));
	}

	private static List<String> names(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.content[*].name");
	}

	private static Map<String, Object> offer(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.content[0].offer");
	}

	private static List<Map<String, Object>> lines(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.content[0].offer.roomLines");
	}

	private static Map<String, Object> cancellation(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.content[0].offer.roomLines[0].cancellation");
	}

	private static List<Long> ratePlanIds(MvcTestResult result) {
		return lines(result).stream().map(line -> ((Number) line.get("ratePlanId")).longValue()).toList();
	}

	// The first room line's room type of every hotel on the page.
	private static List<Long> roomTypeIds(MvcTestResult result) {
		List<Number> ids = JsonPath.read(body(result), "$.content[*].offer.roomLines[0].roomTypeId");
		return ids.stream().map(Number::longValue).toList();
	}

	private static List<List<Long>> roomTypeQuantities(MvcTestResult result) {
		return lines(result).stream()
			.map(line -> List.of(((Number) line.get("roomTypeId")).longValue(),
					((Number) line.get("quantity")).longValue()))
			.toList();
	}

	private static long money(Object money) {
		return ((Number) ((Map<?, ?>) money).get("amount")).longValue();
	}

	private static String currency(Object money) {
		return (String) ((Map<?, ?>) money).get("currency");
	}

}
