package com.company.project.destination.controller;

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
 * Public destination endpoints, called without a token. Other tests feature destinations too, so these
 * tests look only at their own slugs and their relative order.
 */
@IntegrationTest
class DestinationControllerTest {

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
	void popularListsFeaturedDestinationsWithTheirBestHotels() {
		String first = "Popular One " + suffix;
		CreatedHotel best = catalog.hotel("Best " + suffix).city(first).userRating("9.0", 200).create();
		catalog.roomType(best.id(), 2, 0, 2, 2);
		catalog.photo(best.id(), "https://img.test/best.jpg", true, 0);
		catalog.hotel("Good " + suffix).city(first).userRating("8.0", 50).create();
		catalog.hotel("Okay " + suffix).city(first).userRating("7.0", 10).create();
		catalog.hotel("Hidden " + suffix).city(first).userRating("9.9", 999).status("INACTIVE").create();
		String second = "Popular Two " + suffix;
		catalog.hotel("Only " + suffix).city(second).create();
		syncService.sync();
		String firstSlug = "vn-popular-one-" + suffix;
		String secondSlug = "vn-popular-two-" + suffix;
		feature(secondSlug, 2, null);
		feature(firstSlug, 1, "https://img.test/one.jpg");

		MvcTestResult result = mvc.get().uri("/api/destinations/popular?limit=20&hotels=2").exchange();

		assertThat(result).hasStatusOk();
		List<String> slugs = JsonPath.read(body(result), "$[*].slug");
		assertThat(slugs).containsSubsequence(firstSlug, secondSlug);
		Map<String, Object> one = entry(result, firstSlug);
		assertThat(one).containsEntry("name", first).containsEntry("imageUrl", "https://img.test/one.jpg")
			.containsEntry("hotelCount", 3);
		assertThat(JsonPath.<List<String>>read(one, "$.hotels[*].name")).containsExactly("Best " + suffix, "Good " + suffix);
		assertThat(JsonPath.<String>read(one, "$.hotels[0].coverPhotoUrl")).isEqualTo("https://img.test/best.jpg");
		assertThat(JsonPath.<List<String>>read(entry(result, secondSlug), "$.hotels[*].name")).containsExactly("Only " + suffix);
	}

	@Test
	void clickingADestinationRunsTheDocumentedDefaultSearch() {
		String city = "Click City " + suffix;
		CreatedHotel hotel = catalog.hotel("Clicked " + suffix).city(city).create();
		catalog.roomType(hotel.id(), 2, 0, 2, 1);
		syncService.sync();
		LocalDate tomorrow = LocalDate.now().plusDays(1);

		MvcTestResult result = mvc.get()
			.uri("/api/search/hotels?destination={slug}&checkIn={in}&checkOut={out}&rooms=1&adults=2",
					"vn-click-city-" + suffix, tomorrow, tomorrow.plusDays(1))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(JsonPath.<List<String>>read(body(result), "$.content[*].name")).containsExactly("Clicked " + suffix);
	}

	@Test
	void findsADestinationBySlug() {
		catalog.hotel("Hotel " + suffix).city("Slug City " + suffix).create();
		syncService.sync();

		assertThat(mvc.get().uri("/api/destinations/{slug}", "vn-slug-city-" + suffix))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Slug City " + suffix);
		assertThat(mvc.get().uri("/api/destinations/{slug}", "nowhere-" + suffix)).hasStatus(HttpStatus.NOT_FOUND);
	}

	private void feature(String slug, int rank, String imageUrl) {
		jdbc.sql("update destinations set featured_rank = :rank, image_url = :imageUrl where slug = :slug")
			.param("rank", rank)
			.param("imageUrl", imageUrl)
			.param("slug", slug)
			.update();
	}

	private static Map<String, Object> entry(MvcTestResult result, String slug) {
		List<Map<String, Object>> matches = JsonPath.read(body(result), "$[?(@.slug == '" + slug + "')]");
		assertThat(matches).hasSize(1);
		return matches.getFirst();
	}

}
