package com.company.project.search.controller;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * Hotel counts per filter option (C-SRCH-02). Each test builds the same four hotels in its own city (2-night stays,
 * 1 room, 2 adults):
 * <ul>
 * <li>One: 4★ resort, Google 4.6, users 9.2 (100), pool + wifi, 800 m; king bed, balcony, sea view; room only
 * 800k free cancellation, or breakfast 1,500k non-refundable.</li>
 * <li>Two: 4★ hotel, Google 4.1, users 8.3 (40), wifi, 2.5 km; double bed, balcony; room only 1,000k
 * non-refundable.</li>
 * <li>Three: 5★ hotel, Google 3.6, users 7.4 (10), wifi + spa, 6 km; twin beds; breakfast 2,000k free
 * cancellation.</li>
 * <li>Four: 3★ hostel without ratings, facilities or distance; single bed; room only 400k free cancellation.</li>
 * </ul>
 */
@IntegrationTest
class SearchControllerFilterCountsTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	// Lists whose options are combined with "all of"; the others take one value or "any of".
	static final List<String> AND_LISTS = List.of("facilities", "roomFeatures");

	static final List<String> LISTS = List.of("stars", "minGoogleRating", "minUserRating", "accommodationTypes",
			"facilities", "maxDistanceM", "boardTypes", "cancellation", "bedTypes", "roomFeatures");

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DestinationSyncService syncService;

	CatalogFixtures catalog;

	String suffix;

	String city;

	CreatedHotel one;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		city = "Count City " + suffix;
		long strict = catalog.cancellationPolicy("Non-refundable " + suffix, false);

		one = catalog.hotel("One " + suffix).city(city).type("resort").stars(4).googleRating("4.6")
			.userRating("9.2", 100).distance(800).create();
		catalog.hotelAmenity(one.id(), "pool");
		catalog.hotelAmenity(one.id(), "wifi");
		long oneRoom = catalog.bareRoomType(one.id(), 2, 0, 2, 3);
		catalog.bed(oneRoom, "king", 1);
		catalog.roomAmenity(oneRoom, "balcony");
		catalog.roomAmenity(oneRoom, "sea_view");
		catalog.ratePlan(oneRoom).price(800_000).create();
		catalog.ratePlan(oneRoom).board("BB").price(1_500_000).policy(strict).create();

		CreatedHotel two = catalog.hotel("Two " + suffix).city(city).stars(4).googleRating("4.1").userRating("8.3", 40)
			.distance(2_500).create();
		catalog.hotelAmenity(two.id(), "wifi");
		long twoRoom = catalog.bareRoomType(two.id(), 2, 0, 2, 3);
		catalog.bed(twoRoom, "double", 1);
		catalog.roomAmenity(twoRoom, "balcony");
		catalog.ratePlan(twoRoom).price(1_000_000).policy(strict).create();

		CreatedHotel three = catalog.hotel("Three " + suffix).city(city).stars(5).googleRating("3.6")
			.userRating("7.4", 10).distance(6_000).create();
		catalog.hotelAmenity(three.id(), "wifi");
		catalog.hotelAmenity(three.id(), "spa");
		long threeRoom = catalog.bareRoomType(three.id(), 2, 0, 2, 3);
		catalog.bed(threeRoom, "twin", 1);
		catalog.ratePlan(threeRoom).board("BB").price(2_000_000).create();

		CreatedHotel four = catalog.hotel("Four " + suffix).city(city).type("hostel").stars(3).create();
		long fourRoom = catalog.bareRoomType(four.id(), 2, 0, 2, 3);
		catalog.bed(fourRoom, "single", 1);
		catalog.ratePlan(fourRoom).price(400_000).create();
		syncService.sync();
	}

	@Test
	void countsOfAnyOfListsIgnoreTheirOwnSelection() {
		MvcTestResult all = counts("");
		assertThat(hotels(all, "stars")).containsExactly(entry("1", 0), entry("2", 0), entry("3", 1), entry("4", 2),
				entry("5", 1));
		assertThat(hotels(counts("&stars=5"), "stars")).isEqualTo(hotels(all, "stars"));
		assertThat(hotels(counts("&minUserRating=9"), "stars")).containsExactly(entry("1", 0), entry("2", 0),
				entry("3", 0), entry("4", 1), entry("5", 0));
		assertThat(hotels(all, "accommodationTypes")).containsEntry("hotel", 2L).containsEntry("resort", 1L)
			.containsEntry("hostel", 1L)
			.containsEntry("villa", 0L);
		assertThat(hotels(all, "minGoogleRating")).containsExactly(entry("3.0", 3), entry("3.5", 3), entry("4.0", 2),
				entry("4.5", 1));
		assertThat(hotels(all, "minUserRating")).containsExactly(entry("6", 3), entry("7", 3), entry("8", 2),
				entry("9", 1));
		assertThat(hotels(all, "maxDistanceM")).containsExactly(entry("1000", 1), entry("3000", 2), entry("5000", 2),
				entry("10000", 3));
	}

	@Test
	void countsOfAllOfListsNarrowTheCurrentResults() {
		assertThat(hotels(counts(""), "facilities")).containsEntry("wifi", 3L).containsEntry("pool", 1L)
			.containsEntry("spa", 1L)
			.containsEntry("parking", 0L);
		assertThat(hotels(counts("&facilities=pool"), "facilities")).containsEntry("wifi", 1L)
			.containsEntry("pool", 1L)
			.containsEntry("spa", 0L);
		assertThat(hotels(counts(""), "roomFeatures")).containsEntry("balcony", 2L).containsEntry("sea_view", 1L);
		assertThat(hotels(counts("&roomFeatures=sea_view"), "roomFeatures")).containsEntry("balcony", 1L);
	}

	@Test
	void offerOptionsArePricedTheWayTheListWouldBe() {
		MvcTestResult all = counts("");
		assertThat(hotels(all, "boardTypes")).containsEntry("RO", 3L).containsEntry("BB", 2L).containsEntry("HB", 0L);
		assertThat(hotels(all, "cancellation")).containsExactly(entry("FREE", 3), entry("NON_REFUNDABLE", 2));
		assertThat(hotels(all, "bedTypes")).containsEntry("king", 1L).containsEntry("double", 1L)
			.containsEntry("twin", 1L)
			.containsEntry("single", 1L)
			.containsEntry("queen", 0L);
		// One's breakfast rate costs 1,500k a night and Three's 2,000k: neither fits a 1,000k budget.
		assertThat(hotels(counts("&maxPrice=1000000"), "boardTypes")).containsEntry("RO", 3L).containsEntry("BB", 0L);
		// Without One's room-only rate, its only free-cancellation offer is gone.
		assertThat(hotels(counts("&boardTypes=BB"), "cancellation")).containsExactly(entry("FREE", 1),
				entry("NON_REFUNDABLE", 1));
	}

	@Test
	void everyCountMatchesTheResultListWithThatOptionPicked() {
		for (String base : List.of("", "&maxPrice=1600000&facilities=wifi&boardTypes=RO&cancellation=FREE",
				"&stars=4&roomFeatures=balcony&minGoogleRating=4.0")) {
			Map<String, List<String>> baseParams = params(base);
			String counts = body(counts(base));
			for (String list : LISTS) {
				for (Map<String, Object> option : JsonPath.<List<Map<String, Object>>>read(counts, "$." + list)) {
					Map<String, List<String>> picked = new LinkedHashMap<>(baseParams);
					List<String> values = AND_LISTS.contains(list)
							? new ArrayList<>(picked.getOrDefault(list, List.of())) : new ArrayList<>();
					values.add((String) option.get("value"));
					picked.put(list, values);
					assertThat(((Number) option.get("hotels")).longValue())
						.as("%s=%s with %s", list, option.get("value"), base)
						.isEqualTo(totalElements(search(query(picked))));
				}
			}
		}
	}

	@Test
	void priceRangeIsPerNightAndIgnoresThePriceFilter() {
		CreatedHotel dollars = catalog.hotel("Dollars " + suffix).city(city).currency("USD").create();
		catalog.ratePlan(catalog.bareRoomType(dollars.id(), 2, 0, 2, 3)).currency("USD").price(1_000).create();

		MvcTestResult all = counts("&maxPrice=500000");
		assertThat(all).bodyJson().extractingPath("$.currency").isEqualTo("VND");
		assertThat(all).bodyJson().extractingPath("$.priceRange.min.amount").isEqualTo(400_000);
		assertThat(all).bodyJson().extractingPath("$.priceRange.max.amount").isEqualTo(2_000_000);
		assertThat(counts("&stars=5")).bodyJson().extractingPath("$.priceRange.min.amount").isEqualTo(2_000_000);
		assertThat(JsonPath.<Number>read(body(counts("&currency=USD")), "$.priceRange.max.amount").doubleValue())
			.isEqualTo(10.0);
		MvcTestResult nothingFree = counts("rooms=4&adults=4", "");
		assertThat(nothingFree).bodyJson().extractingPath("$.priceRange").isNull();
		assertThat(hotels(nothingFree, "stars")).containsOnly(entry("1", 0), entry("2", 0), entry("3", 0),
				entry("4", 0), entry("5", 0));
	}

	@Test
	void listsEveryOptionWithItsName() {
		MvcTestResult all = counts("");

		assertThat(JsonPath.<List<String>>read(body(all), "$.boardTypes[*].value")).containsExactly("RO", "BB", "HB",
				"FB", "AI");
		assertThat(JsonPath.<List<String>>read(body(all), "$.boardTypes[?(@.value == 'BB')].name"))
			.containsExactly("Breakfast included");
		assertThat(JsonPath.<List<String>>read(body(all), "$.facilities[?(@.value == 'pool')].category"))
			.containsExactly("Wellness");
		assertThat(JsonPath.<List<String>>read(body(all), "$.facilities[*].value")).contains("wifi", "pool")
			.doesNotContain("balcony");
		assertThat(JsonPath.<List<String>>read(body(all), "$.roomFeatures[*].value")).contains("balcony")
			.doesNotContain("wifi");
		assertThat(JsonPath.<List<String>>read(body(all), "$.bedTypes[*].value")).containsExactly("single", "double",
				"queen", "king", "twin");
		assertThat(JsonPath.<List<String>>read(body(all), "$.cancellation[*].name")).containsExactly(
				"Free cancellation", "Non-refundable");
		assertThat(JsonPath.<List<Object>>read(body(all), "$.stars[*].name")).containsOnlyNulls();
	}

	@Test
	void countsOneHotelBySlugAndRejectsWhatTheListRejects() {
		MvcTestResult hotel = mvc.get()
			.uri("/api/search/hotels/filters?hotel=%s&checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(one.slug(),
					CHECK_IN, CHECK_OUT))
			.exchange();
		assertThat(hotels(hotel, "stars")).containsEntry("4", 1L).containsEntry("5", 0L);
		assertThat(hotels(hotel, "boardTypes")).containsEntry("RO", 1L).containsEntry("BB", 1L);

		assertThat(mvc.get()
			.uri("/api/search/hotels/filters?destination=nowhere-%s&checkIn=%s&checkOut=%s&rooms=1&adults=2"
				.formatted(suffix, CHECK_IN, CHECK_OUT))
			.exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(counts("&stars=9")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void aPlaceWithNothingListedCountsNothing() {
		String empty = "Empty City " + suffix;
		CreatedHotel gone = catalog.hotel("Gone " + suffix).city(empty).create();
		catalog.roomType(gone.id(), 2, 0, 2, 3);
		syncService.sync();
		catalog.hotelStatus(gone.id(), "INACTIVE"); // the destination stays until the next sync
		String query = "destination=vn-empty-city-%s&checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(suffix,
				CHECK_IN, CHECK_OUT);

		MvcTestResult counts = mvc.get().uri("/api/search/hotels/filters?" + query).exchange();
		assertThat(counts).bodyJson().extractingPath("$.currency").isNull();
		assertThat(counts).bodyJson().extractingPath("$.priceRange").isNull();
		assertThat(hotels(counts, "boardTypes")).containsOnly(entry("RO", 0), entry("BB", 0), entry("HB", 0),
				entry("FB", 0), entry("AI", 0));
		assertThat(totalElements(mvc.get().uri("/api/search/hotels?" + query).exchange())).isZero();
	}

	private MvcTestResult counts(String filters) {
		return counts("rooms=1&adults=2", filters);
	}

	private MvcTestResult counts(String guests, String filters) {
		return mvc.get()
			.uri("/api/search/hotels/filters?destination=vn-count-city-%s&checkIn=%s&checkOut=%s&%s%s".formatted(suffix,
					CHECK_IN, CHECK_OUT, guests, filters))
			.exchange();
	}

	private MvcTestResult search(String filters) {
		return mvc.get()
			.uri("/api/search/hotels?destination=vn-count-city-%s&checkIn=%s&checkOut=%s&rooms=1&adults=2%s"
				.formatted(suffix, CHECK_IN, CHECK_OUT, filters))
			.exchange();
	}

	private static long totalElements(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.<Number>read(body(result), "$.page.totalElements").longValue();
	}

	// Option value → hotels, in the response's order.
	private static Map<String, Long> hotels(MvcTestResult result, String list) {
		assertThat(result).hasStatusOk();
		List<Map<String, Object>> options = JsonPath.read(body(result), "$." + list);
		return options.stream()
			.collect(Collectors.toMap(option -> (String) option.get("value"),
					option -> ((Number) option.get("hotels")).longValue(), (a, b) -> a, LinkedHashMap::new));
	}

	private static Map.Entry<String, Long> entry(String value, long hotels) {
		return Map.entry(value, hotels);
	}

	private static Map<String, List<String>> params(String query) {
		Map<String, List<String>> params = new LinkedHashMap<>();
		for (String pair : query.split("&")) {
			if (!pair.isEmpty()) {
				String[] parts = pair.split("=", 2);
				params.computeIfAbsent(parts[0], name -> new ArrayList<>()).add(parts[1]);
			}
		}
		return params;
	}

	private static String query(Map<String, List<String>> params) {
		return params.entrySet()
			.stream()
			.flatMap(entry -> entry.getValue().stream().map(value -> "&" + entry.getKey() + "=" + value))
			.collect(Collectors.joining());
	}

}
