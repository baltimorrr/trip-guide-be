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
 * Homepage search, called without a token like a guest would. Each test builds its own city
 * ({@link CatalogFixtures#uniqueSuffix()}) and asserts only on the hotels in it.
 */
@IntegrationTest
class SearchControllerTest {

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
	void suggestsPlacesAndHotelsIgnoringAccentsAndCase() {
		CreatedHotel hotel = catalog.hotel("Sông Hàn Riverside " + suffix)
			.city("Đà Nẵng " + suffix)
			.district("Hải Châu " + suffix)
			.ward("Thạch Thang " + suffix)
			.create();
		syncService.sync();

		MvcTestResult city = suggest("DA NANG " + suffix);
		assertThat(city).hasStatusOk();
		assertThat(destination(city, "vn-da-nang-" + suffix))
			.containsEntry("type", "CITY")
			.containsEntry("name", "Đà Nẵng " + suffix)
			.containsEntry("context", "Vietnam")
			.containsEntry("hotelCount", 1);

		MvcTestResult ward = suggest("thach thang " + suffix);
		assertThat(destination(ward, "vn-da-nang-%s-hai-chau-%s-thach-thang-%s".formatted(suffix, suffix, suffix)))
			.containsEntry("context", "Hải Châu %s, Đà Nẵng %s, Vietnam".formatted(suffix, suffix));

		// Second word of the hotel name, without accents.
		MvcTestResult byHotelName = suggest("riverside " + suffix);
		List<Map<String, Object>> hotels = JsonPath.read(body(byHotelName), "$.hotels");
		assertThat(hotels).singleElement().satisfies(found -> assertThat(found)
			.containsEntry("slug", hotel.slug())
			.containsEntry("context", "Hải Châu %s, Đà Nẵng %s".formatted(suffix, suffix)));
	}

	@Test
	void suggestsPlacesByNameDespiteHalfTypedWordsAndTypos() {
		catalog.hotel("Hotel " + suffix)
			.city("Đà Nẵng " + suffix)
			.district("Hải Châu " + suffix)
			.ward("Thạch Thang " + suffix)
			.create();
		syncService.sync();
		String district = "vn-da-nang-%s-hai-chau-%s".formatted(suffix, suffix);

		assertThat(destinationSlugs(suggest("hai chau " + suffix.substring(0, 5)))).first().isEqualTo(district);
		assertThat(destinationSlugs(suggest("thach thnag " + suffix))).first()
			.isEqualTo(district + "-thach-thang-" + suffix);
	}

	@Test
	void doesNotSuggestPlacesWithoutListedHotels() {
		catalog.hotel("Closed " + suffix).city("Huế " + suffix).status("INACTIVE").create();
		syncService.sync();

		MvcTestResult result = suggest("hue " + suffix);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.destinations").asArray().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.hotels").asArray().isEmpty();
	}

	@Test
	void typedWildcardsMatchOnlyThemselves() {
		MvcTestResult result = suggest("%_");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.destinations").asArray().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.hotels").asArray().isEmpty();
	}

	@Test
	void typedUnderscoreMatchesOnlyAnUnderscore() {
		catalog.hotel("Under_score " + suffix).city("Escape City " + suffix).create();
		catalog.hotel("Underxscore " + suffix).city("Escape City " + suffix).create();

		MvcTestResult result = suggest("under_score " + suffix);

		assertThat(result).hasStatusOk();
		assertThat(JsonPath.<List<String>>read(body(result), "$.hotels[*].name")).containsExactly("Under_score " + suffix);
	}

	@Test
	void suggestionsNeedAtLeastTwoCharacters() {
		assertThat(suggest(" a ")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void returnsOnlyHotelsWithAFittingRoomFreeEveryNight() {
		String city = "Search City " + suffix;
		CreatedHotel available = catalog.hotel("Available " + suffix).city(city).create();
		catalog.roomType(available.id(), 2, 0, 2, 3);
		catalog.photo(available.id(), "https://img.test/second.jpg", false, 1);
		catalog.photo(available.id(), "https://img.test/cover.jpg", true, 2);

		CreatedHotel closedNight = catalog.hotel("Closed night " + suffix).city(city).create();
		long closedRoom = catalog.roomType(closedNight.id(), 2, 0, 2, 3);
		catalog.inventory(closedRoom, CHECK_IN.plusDays(1), 3, 0, true);

		CreatedHotel soldOut = catalog.hotel("Sold out " + suffix).city(city).create();
		long soldOutRoom = catalog.roomType(soldOut.id(), 2, 0, 2, 2);
		catalog.inventory(soldOutRoom, CHECK_IN, 2, 2, false);

		CreatedHotel tooSmall = catalog.hotel("Too small " + suffix).city(city).create();
		catalog.roomType(tooSmall.id(), 1, 0, 1, 5);

		CreatedHotel draft = catalog.hotel("Draft " + suffix).city(city).status("DRAFT").create();
		catalog.roomType(draft.id(), 2, 0, 2, 3);

		catalog.hotel("No rooms " + suffix).city(city).create();
		syncService.sync();

		MvcTestResult result = search("destination=vn-search-city-%s&%s&rooms=1&adults=2".formatted(suffix, stay()));

		assertThat(result).hasStatusOk();
		assertThat(names(result)).containsExactly("Available " + suffix);
		assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.content[0].coverPhotoUrl").isEqualTo("https://img.test/cover.jpg");
		assertThat(result).bodyJson().extractingPath("$.content[0].accommodationType").isEqualTo("hotel");
		assertThat(result).bodyJson().extractingPath("$.content[0].city").isEqualTo(city);
	}

	@Test
	void severalRoomsCanComeFromDifferentRoomTypes() {
		CreatedHotel hotel = catalog.hotel("Mixed " + suffix).city("Rooms City " + suffix).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 1);
		catalog.roomType(hotel.id(), 2, 0, 2, 1);
		syncService.sync();
		String destination = "destination=vn-rooms-city-" + suffix;

		assertThat(names(search("%s&%s&rooms=2&adults=4".formatted(destination, stay())))).containsExactly("Mixed " + suffix);
		assertThat(names(search("%s&%s&rooms=3&adults=3".formatted(destination, stay())))).isEmpty();
	}

	@Test
	void childrenMayUsePlacesAdultsLeaveFree() {
		String city = "Family City " + suffix;
		CreatedHotel withChildBed = catalog.hotel("Child bed " + suffix).city(city).create();
		catalog.roomType(withChildBed.id(), 2, 1, 3, 1);
		CreatedHotel adultsOnly = catalog.hotel("Adult beds " + suffix).city(city).create();
		catalog.roomType(adultsOnly.id(), 2, 0, 3, 1);
		syncService.sync();
		String destination = "destination=vn-family-city-" + suffix;

		assertThat(names(search("%s&%s&rooms=1&adults=2&childAges=6".formatted(destination, stay()))))
			.containsExactly("Child bed " + suffix);
		assertThat(names(search("%s&%s&rooms=1&adults=1&childAges=6".formatted(destination, stay()))))
			.containsExactlyInAnyOrder("Child bed " + suffix, "Adult beds " + suffix);
	}

	@Test
	void searchesOneHotelBySlug() {
		CreatedHotel hotel = catalog.hotel("Single " + suffix).city("Single City " + suffix).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 1);

		assertThat(names(search("hotel=%s&%s&rooms=1&adults=2".formatted(hotel.slug(), stay()))))
			.containsExactly("Single " + suffix);
		assertThat(search("hotel=missing-%s&%s&rooms=1&adults=2".formatted(suffix, stay())))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void needsExactlyOneLocation() {
		assertThat(search("%s&rooms=1&adults=2".formatted(stay()))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("destination=vn&hotel=x&%s&rooms=1&adults=2".formatted(stay())))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Choose either a destination or a hotel");
	}

	@Test
	void unknownDestinationIsNotFound() {
		assertThat(search("destination=nowhere-%s&%s&rooms=1&adults=2".formatted(suffix, stay())))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectsInvalidCriteria() {
		String place = "destination=nowhere-" + suffix;
		String yesterday = LocalDate.now().minusDays(2).toString();
		assertThat(search("%s&checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(place, yesterday, CHECK_OUT)))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Check-in cannot be in the past");
		assertThat(search("%s&checkIn=%s&checkOut=%s&rooms=1&adults=2".formatted(place, CHECK_IN, CHECK_IN.plusDays(31))))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("%s&%s&rooms=9&adults=9".formatted(place, stay()))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("%s&%s&rooms=2&adults=1".formatted(place, stay())))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Each room needs at least one adult");
		assertThat(search("%s&%s&rooms=1&adults=2&childAges=18".formatted(place, stay())))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(search("%s&%s&adults=2".formatted(place, stay()))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void malformedDateIsRejectedWithoutEchoingIt() {
		MvcTestResult result = search("destination=vn&checkIn=not-a-date&checkOut=%s&rooms=1&adults=2".formatted(CHECK_OUT));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("checkIn");
		assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("has an invalid value");
		assertThat(body(result)).doesNotContain("not-a-date");
	}

	private static String stay() {
		return "checkIn=%s&checkOut=%s".formatted(CHECK_IN, CHECK_OUT);
	}

	private MvcTestResult suggest(String query) {
		return mvc.get().uri("/api/search/suggestions").param("q", query).exchange();
	}

	private MvcTestResult search(String query) {
		return mvc.get().uri("/api/search/hotels?" + query).exchange();
	}

	private static List<String> names(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.content[*].name");
	}

	private static List<String> destinationSlugs(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.destinations[*].slug");
	}

	private static Map<String, Object> destination(MvcTestResult result, String slug) {
		List<Map<String, Object>> matches = JsonPath.read(body(result), "$.destinations[?(@.slug == '" + slug + "')]");
		assertThat(matches).hasSize(1);
		return matches.getFirst();
	}

}
