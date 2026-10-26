package com.company.project.search.controller;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

/** Sorting the result list (C-SRCH-03). Each test searches its own city. */
@IntegrationTest
class SearchControllerSortTest {

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
		city = "Sort City " + suffix;
	}

	@Test
	void priceSortListsTheCheapestFirstAndOtherCurrenciesLast() {
		pricedHotel("Mid", 1_000_000);
		pricedHotel("High", 1_200_000);
		pricedHotel("Low", 800_000);
		CreatedHotel dollars = catalog.hotel("Dollars " + suffix).city(city).currency("USD").create();
		catalog.ratePlan(catalog.bareRoomType(dollars.id(), 2, 0, 2, 3)).currency("USD").price(1_000).create();
		syncService.sync();

		assertThat(names(search("&sort=price"))).containsExactly("Low", "Mid", "High", "Dollars");
		assertThat(names(search("&sort=PRICE"))).containsExactly("Low", "Mid", "High", "Dollars");
		assertThat(names(search("&sort=price,desc"))).containsExactly("Low", "Mid", "High", "Dollars");
		assertThat(names(search("&sort=price&currency=USD"))).first().isEqualTo("Dollars");
	}

	@Test
	void ratingSortNeedsAMinimumNumberOfReviews() {
		ratedHotel("Few", "9.8", 3);
		ratedHotel("Many", "8.0", 50);
		ratedHotel("Good", "8.5", 20);
		CreatedHotel unrated = catalog.hotel("Unrated " + suffix).city(city).create();
		catalog.roomType(unrated.id(), 2, 0, 2, 3);
		syncService.sync();

		assertThat(names(search("&sort=rating"))).containsExactly("Good", "Many", "Few", "Unrated");
		// Without a sort the order stays the one from before sorting existed: best average first.
		assertThat(names(search(""))).containsExactly("Few", "Good", "Many", "Unrated");
	}

	@Test
	void distanceSortListsTheNearestFirst() {
		placedHotel("Far", 3_000);
		placedHotel("Near", 500);
		CreatedHotel unknown = catalog.hotel("Unknown " + suffix).city(city).create();
		catalog.roomType(unknown.id(), 2, 0, 2, 3);
		placedHotel("Middle", 1_200);
		syncService.sync();

		assertThat(names(search("&sort=distance"))).containsExactly("Near", "Middle", "Far", "Unknown");
	}

	@Test
	void pagesFollowTheSortOrder() {
		pricedHotel("B", 2_000_000);
		pricedHotel("A", 1_000_000);
		pricedHotel("C", 3_000_000);
		syncService.sync();

		MvcTestResult first = search("&sort=price&size=2&page=0");
		MvcTestResult second = search("&sort=price&size=2&page=1");
		List<String> both = new ArrayList<>(names(first));
		both.addAll(names(second));

		assertThat(both).containsExactly("A", "B", "C");
		assertThat(first).bodyJson().extractingPath("$.page.totalElements").isEqualTo(3);
	}

	@Test
	void rejectsUnknownOrSeveralSorts() {
		assertThat(search("&sort=recommended")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Sort by one of: price, rating, distance");
		assertThat(search("&sort=price&sort=rating")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	private void pricedHotel(String name, long nightPrice) {
		CreatedHotel hotel = catalog.hotel(name + " " + suffix).city(city).create();
		catalog.ratePlan(catalog.bareRoomType(hotel.id(), 2, 0, 2, 3)).price(nightPrice).create();
	}

	private void ratedHotel(String name, String average, int reviews) {
		CreatedHotel hotel = catalog.hotel(name + " " + suffix).city(city).userRating(average, reviews).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 3);
	}

	private void placedHotel(String name, int metres) {
		CreatedHotel hotel = catalog.hotel(name + " " + suffix).city(city).distance(metres).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 3);
	}

	private MvcTestResult search(String extra) {
		return mvc.get()
			.uri("/api/search/hotels?destination=vn-sort-city-%s&checkIn=%s&checkOut=%s&rooms=1&adults=2%s"
				.formatted(suffix, CHECK_IN, CHECK_OUT, extra))
			.exchange();
	}

	// Hotel names without the test's suffix, in page order.
	private List<String> names(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		List<String> names = JsonPath.read(body(result), "$.content[*].name");
		return names.stream().map(name -> name.replace(" " + suffix, "")).toList();
	}

}
