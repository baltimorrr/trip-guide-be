package com.company.project.search.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Result filters (C-SRCH-02). Each test builds its hotels in its own city and searches that destination, so no other
 * test's hotels show up. Stays are 2 nights unless the test says otherwise.
 */
@IntegrationTest
class SearchControllerFilterTest {

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

	String city;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		city = "Filter City " + suffix;
	}

	@Test
	void hotelFiltersDropHotelsThatDoNotMatch() {
		CreatedHotel resort = catalog.hotel("Resort " + suffix).city(city).type("resort").stars(5).googleRating("4.6")
			.userRating("9.1", 100).distance(800).create();
		catalog.roomType(resort.id(), 2, 0, 2, 3);
		catalog.hotelAmenity(resort.id(), "pool");
		catalog.hotelAmenity(resort.id(), "wifi");
		CreatedHotel hotel = catalog.hotel("Hotel " + suffix).city(city).stars(4).googleRating("4.1")
			.userRating("8.2", 50).distance(2500).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 3);
		catalog.hotelAmenity(hotel.id(), "wifi");
		CreatedHotel hostel = catalog.hotel("Hostel " + suffix).city(city).type("hostel").stars(3).create();
		catalog.roomType(hostel.id(), 2, 0, 2, 3);
		syncService.sync();
		String r = "Resort " + suffix;
		String h = "Hotel " + suffix;
		String b = "Hostel " + suffix;

		assertThat(names(search(""))).containsExactlyInAnyOrder(r, h, b);
		assertThat(names(search("&stars=5"))).containsExactly(r);
		assertThat(names(search("&stars=4&stars=5"))).containsExactlyInAnyOrder(r, h);
		assertThat(names(search("&minGoogleRating=4.5"))).containsExactly(r);
		assertThat(names(search("&minUserRating=8.5"))).containsExactly(r);
		assertThat(names(search("&accommodationTypes=resort&accommodationTypes=hostel"))).containsExactlyInAnyOrder(r, b);
		assertThat(names(search("&facilities=wifi"))).containsExactlyInAnyOrder(r, h);
		assertThat(names(search("&facilities=wifi&facilities=pool"))).containsExactly(r);
		assertThat(names(search("&maxDistanceM=1000"))).containsExactly(r);
		assertThat(names(search("&maxDistanceM=3000"))).containsExactlyInAnyOrder(r, h);
		assertThat(names(search("&stars=4&stars=5&maxDistanceM=1000"))).containsExactly(r);
		assertThat(names(search("&facilities=no-such-facility"))).isEmpty();
		assertThat(search("&stars=4&stars=5&maxDistanceM=1000")).bodyJson()
			.extractingPath("$.page.totalElements")
			.isEqualTo(1);
	}

	@Test
	void nameFilterIgnoresAccentsAndMatchesWildcardsLiterally() {
		CreatedHotel beach = catalog.hotel("Bãi Biển Resort " + suffix).city(city).create();
		catalog.roomType(beach.id(), 2, 0, 2, 3);
		CreatedHotel percent = catalog.hotel("100% Fun " + suffix).city(city).create();
		catalog.roomType(percent.id(), 2, 0, 2, 3);
		syncService.sync();

		assertThat(names(searchByName("bien"))).containsExactly("Bãi Biển Resort " + suffix);
		assertThat(names(searchByName("BIỂN resort"))).containsExactly("Bãi Biển Resort " + suffix);
		assertThat(names(searchByName("%"))).containsExactly("100% Fun " + suffix);
		assertThat(names(searchByName("  "))).hasSize(2); // blank: no filter
	}

	@Test
	void boardFilterShowsTheCheapestMatchingRate() {
		CreatedHotel both = catalog.hotel("Both boards " + suffix).city(city).create();
		long room = catalog.bareRoomType(both.id(), 2, 0, 2, 3);
		catalog.ratePlan(room).price(900_000).create();
		long breakfast = catalog.ratePlan(room).board("BB").price(1_000_000).create();
		CreatedHotel roomOnly = catalog.hotel("Room only " + suffix).city(city).create();
		catalog.roomType(roomOnly.id(), 2, 0, 2, 3);
		syncService.sync();

		MvcTestResult all = search("");
		assertThat(total(all, "Both boards " + suffix)).isEqualTo(1_800_000);
		MvcTestResult withBreakfast = search("&boardTypes=BB");
		assertThat(names(withBreakfast)).containsExactly("Both boards " + suffix);
		assertThat(total(withBreakfast, "Both boards " + suffix)).isEqualTo(2_000_000);
		assertThat(JsonPath.<List<Number>>read(body(withBreakfast), "$.content[0].offer.roomLines[*].ratePlanId"))
			.extracting(Number::longValue)
			.containsExactly(breakfast);
		assertThat(names(search("&boardTypes=BB&boardTypes=RO"))).hasSize(2);
	}

	@Test
	void cancellationFilterKnowsWhetherFreeCancellationIsStillPossible() {
		LocalDate tomorrow = LocalDate.now().plusDays(1);
		String stay = "checkIn=%s&checkOut=%s".formatted(tomorrow, tomorrow.plusDays(1));
		CreatedHotel free = catalog.hotel("Free " + suffix).city(city).create();
		catalog.roomType(free.id(), 2, 0, 2, 3); // free until check-in
		CreatedHotel strict = catalog.hotel("Strict " + suffix).city(city).create();
		catalog.ratePlan(catalog.bareRoomType(strict.id(), 2, 0, 2, 3))
			.policy(catalog.cancellationPolicy("Non-refundable " + suffix, false))
			.create();
		CreatedHotel passed = catalog.hotel("Passed " + suffix).city(city).create();
		long twoDays = catalog.cancellationPolicy("Free until 48h " + suffix, true);
		catalog.cancellationRule(twoDays, 48, "PERCENT", "100");
		catalog.ratePlan(catalog.bareRoomType(passed.id(), 2, 0, 2, 3)).policy(twoDays).create();
		syncService.sync();

		assertThat(names(search(stay, ""))).containsExactlyInAnyOrder("Free " + suffix, "Strict " + suffix,
				"Passed " + suffix);
		MvcTestResult freeOnly = search(stay, "&cancellation=FREE");
		assertThat(names(freeOnly)).containsExactly("Free " + suffix);
		assertThat(JsonPath.<String>read(body(freeOnly), "$.content[0].offer.roomLines[0].cancellation.freeUntil"))
			.isNotNull();
		assertThat(names(search(stay, "&cancellation=NON_REFUNDABLE"))).containsExactly("Strict " + suffix);
		assertThat(names(search(stay, "&cancellation=FREE&cancellation=NON_REFUNDABLE")))
			.containsExactlyInAnyOrder("Free " + suffix, "Strict " + suffix);
	}

	@Test
	void bedAndRoomFeatureFiltersChooseTheRoomTypesOfTheOffer() {
		CreatedHotel hotel = catalog.hotel("Rooms " + suffix).city(city).create();
		long king = catalog.bareRoomType(hotel.id(), 2, 0, 2, 1);
		catalog.ratePlan(king).price(1_500_000).create();
		catalog.bed(king, "king", 1);
		catalog.roomAmenity(king, "balcony");
		catalog.roomAmenity(king, "sea_view");
		long twoDoubles = catalog.bareRoomType(hotel.id(), 2, 0, 2, 2);
		catalog.ratePlan(twoDoubles).price(1_000_000).create();
		catalog.bed(twoDoubles, "double", 1);
		catalog.roomAmenity(twoDoubles, "balcony");
		syncService.sync();

		assertThat(roomTypes(search("&bedTypes=king"))).containsExactly(List.of(king, 1L));
		assertThat(roomTypes(search("&bedTypes=king&bedTypes=double"))).containsExactly(List.of(twoDoubles, 1L));
		assertThat(roomTypes(search("&roomFeatures=balcony"))).containsExactly(List.of(twoDoubles, 1L));
		assertThat(roomTypes(search("&roomFeatures=balcony&roomFeatures=sea_view"))).containsExactly(List.of(king, 1L));
		assertThat(roomTypes(search("rooms=3&adults=3", "&roomFeatures=balcony")))
			.containsExactly(List.of(twoDoubles, 2L), List.of(king, 1L));
		assertThat(names(search("rooms=2&adults=2", "&bedTypes=king"))).isEmpty();
		assertThat(names(search("&bedTypes=single"))).isEmpty();
	}

	@Test
	void priceRangeIsPerNightOfTheWholeOfferInOneCurrency() {
		CreatedHotel hotel = catalog.hotel("Million " + suffix).city(city).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 3); // 1,000,000 VND a night
		CreatedHotel other = catalog.hotel("Second " + suffix).city(city).create();
		catalog.ratePlan(catalog.bareRoomType(other.id(), 2, 0, 2, 3)).price(3_000_000).create();
		CreatedHotel dollars = catalog.hotel("Dollars " + suffix).city(city).currency("USD").create();
		catalog.ratePlan(catalog.bareRoomType(dollars.id(), 2, 0, 2, 3)).currency("USD").price(5_000).create();
		syncService.sync();
		String million = "Million " + suffix;

		assertThat(names(search(""))).hasSize(3);
		assertThat(names(search("&maxPrice=1000000"))).containsExactly(million);
		assertThat(names(search("&maxPrice=999999"))).isEmpty();
		assertThat(names(search("&minPrice=1000000&maxPrice=1000000"))).containsExactly(million);
		assertThat(names(search("&minPrice=1000001&maxPrice=3000000"))).containsExactly("Second " + suffix);
		assertThat(names(search("rooms=2&adults=2", "&maxPrice=1000000"))).isEmpty();
		assertThat(names(search("rooms=2&adults=2", "&maxPrice=2000000"))).containsExactly(million);
		// Most hotels here sell in VND, so prices are compared in VND unless the request names a currency.
		assertThat(names(search("&maxPrice=60"))).isEmpty();
		assertThat(names(search("&currency=USD&maxPrice=50"))).containsExactly("Dollars " + suffix);
		assertThat(names(search("&currency=USD&maxPrice=49.99"))).isEmpty();
	}

	@Test
	void rejectsInvalidFiltersWithoutEchoingThem() {
		CreatedHotel hotel = catalog.hotel("Valid " + suffix).city(city).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 3);
		syncService.sync();

		assertThat(search("&stars=6")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("&minUserRating=11")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("&maxDistanceM=0")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("&currency=vnd")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("&minPrice=2&maxPrice=1")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("The lowest price cannot be above the highest price");
		assertThat(search("&currency=XXZ")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Unknown currency");
		MvcTestResult badEnum = search("&cancellation=SOMETIMES");
		assertThat(badEnum).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(badEnum)).doesNotContain("SOMETIMES");
	}

	private MvcTestResult search(String filters) {
		return search("rooms=1&adults=2", filters);
	}

	private MvcTestResult search(String guestsOrStay, String filters) {
		String stay = guestsOrStay.startsWith("checkIn") ? guestsOrStay + "&rooms=1&adults=2"
				: "checkIn=%s&checkOut=%s&%s".formatted(CHECK_IN, CHECK_OUT, guestsOrStay);
		return mvc.get().uri("/api/search/hotels?destination=vn-filter-city-%s&%s%s".formatted(suffix, stay, filters))
			.exchange();
	}

	// As a parameter, so the URI template does not encode "%" a second time.
	private MvcTestResult searchByName(String name) {
		return mvc.get()
			.uri("/api/search/hotels?destination=vn-filter-city-%s&checkIn=%s&checkOut=%s&rooms=1&adults=2"
				.formatted(suffix, CHECK_IN, CHECK_OUT))
			.param("name", name)
			.exchange();
	}

	private static List<String> names(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.content[*].name");
	}

	private static long total(MvcTestResult result, String name) {
		assertThat(result).hasStatusOk();
		List<Number> totals = JsonPath.read(body(result),
				"$.content[?(@.name == '%s')].offer.totalPrice.amount".formatted(name));
		return totals.getFirst().longValue();
	}

	// [room type id, quantity] of each room line of the first hotel.
	private static List<List<Long>> roomTypes(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		List<Map<String, Object>> lines = JsonPath.read(body(result), "$.content[0].offer.roomLines");
		return lines.stream()
			.map(line -> List.of(((Number) line.get("roomTypeId")).longValue(),
					((Number) line.get("quantity")).longValue()))
			.toList();
	}

}
