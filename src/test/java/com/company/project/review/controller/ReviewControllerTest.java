package com.company.project.review.controller;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

/** A hotel's published reviews on its public page (C-HOTEL-07). Every test reviews a hotel of its own. */
@IntegrationTest
class ReviewControllerTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	CreatedHotel hotel;

	long reviewer;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		hotel = catalog.hotel("Reviewed " + suffix).create();
		reviewer = catalog.user();
	}

	@Test
	void listsNewestFirstWithEveryField() {
		long old = catalog.review(hotel.id(), reviewer, 6).createdAt(day(1)).create();
		long newest = catalog.review(hotel.id(), reviewer, 9)
			.title("Great stay " + suffix)
			.body("Friendly staff.")
			.travellerType("FAMILY")
			.createdAt(day(3))
			.ownerReply("Thank you!", day(4))
			.create();
		long middle = catalog.review(hotel.id(), reviewer, 8).createdAt(day(2)).create();

		MvcTestResult result = reviews("");

		assertThat(ids(result)).containsExactly(newest, middle, old);
		Map<String, Object> first = JsonPath.read(body(result), "$.content[0]");
		assertThat(first).containsEntry("score", 9)
			.containsEntry("title", "Great stay " + suffix)
			.containsEntry("body", "Friendly staff.")
			.containsEntry("travellerType", "FAMILY")
			.containsEntry("createdAt", "2026-01-03T10:00:00Z")
			.containsEntry("ownerReply", "Thank you!")
			.containsEntry("ownerRepliedAt", "2026-01-04T10:00:00Z")
			.doesNotContainKeys("userId", "bookingId", "status");
		assertThat(result).bodyJson().extractingPath("$.content[1].travellerType").isNull();
	}

	@Test
	void sortsByScoreOrDateWithTheNewestFirstOnTies() {
		long a = catalog.review(hotel.id(), reviewer, 9).createdAt(day(1)).create();
		long b = catalog.review(hotel.id(), reviewer, 7).createdAt(day(2)).create();
		long c = catalog.review(hotel.id(), reviewer, 9).createdAt(day(3)).create();
		long d = catalog.review(hotel.id(), reviewer, 5).createdAt(day(4)).create();

		assertThat(ids(reviews("?sort=score,desc"))).containsExactly(c, a, b, d);
		assertThat(ids(reviews("?sort=score,asc"))).containsExactly(d, b, c, a);
		assertThat(ids(reviews("?sort=createdAt,asc"))).containsExactly(a, b, c, d);
		assertThat(ids(reviews("?sort=createdAt,desc"))).containsExactly(d, c, b, a);
	}

	@Test
	void hiddenAndOtherHotelsReviewsAreNeitherListedNorCounted() {
		long first = catalog.review(hotel.id(), reviewer, 8).createdAt(day(1)).create();
		long second = catalog.review(hotel.id(), reviewer, 7).createdAt(day(2)).create();
		long third = catalog.review(hotel.id(), reviewer, 6).createdAt(day(3)).create();
		catalog.review(hotel.id(), reviewer, 1).hidden().createdAt(day(4)).create();
		CreatedHotel other = catalog.hotel("Other " + suffix).create();
		catalog.review(other.id(), reviewer, 10).createdAt(day(5)).create();

		MvcTestResult page = reviews("?size=2");

		assertThat(ids(page)).containsExactly(third, second);
		assertThat(page).bodyJson().extractingPath("$.page.totalElements").isEqualTo(3);
		assertThat(page).bodyJson().extractingPath("$.page.totalPages").isEqualTo(2);
		assertThat(ids(reviews("?size=2&page=1"))).containsExactly(first);
	}

	@Test
	void sortingByAnyOtherPropertyIsRejected() {
		MvcTestResult byBody = reviews("?sort=body");

		assertThat(byBody).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(byBody.getResponse().getContentType()).isEqualTo("application/problem+json");
		assertThat(byBody).bodyJson().extractingPath("$.detail").isEqualTo("Reviews can be sorted by createdAt or score");
		assertThat(reviews("?sort=id,desc")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void onlyListedHotelsShowReviews() {
		CreatedHotel inactive = catalog.hotel("Inactive " + suffix).status("INACTIVE").create();
		catalog.review(inactive.id(), reviewer, 9).create();

		assertThat(mvc.get().uri("/api/hotels/no-such-hotel-{s}/reviews", suffix)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/hotels/{slug}/reviews", inactive.slug())).hasStatus(HttpStatus.NOT_FOUND);
		MvcTestResult none = reviews("");
		assertThat(none).hasStatusOk();
		assertThat(none).bodyJson().extractingPath("$.content").asArray().isEmpty();
		assertThat(none).bodyJson().extractingPath("$.page.totalElements").isEqualTo(0);
	}

	private MvcTestResult reviews(String query) {
		return mvc.get().uri("/api/hotels/%s/reviews%s".formatted(hotel.slug(), query)).exchange();
	}

	private static List<Long> ids(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		List<Number> ids = JsonPath.read(body(result), "$.content[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private static Instant day(int dayOfJanuary) {
		return Instant.parse("2026-01-%02dT10:00:00Z".formatted(dayOfJanuary));
	}

}
