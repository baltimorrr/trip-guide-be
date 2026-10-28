package com.company.project.wishlist.controller;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.userToken;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Saving, listing and removing hotels in the signed-in user's wishlist (C-SRCH-08). Every test signs up a new user,
 * so its wishlist holds only what the test saved.
 */
@IntegrationTest
class WishlistControllerTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	String token;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		token = userToken(mvc);
	}

	@Test
	void savingTwiceKeepsOneEntry() {
		CreatedHotel hotel = catalog.hotel("Saved " + suffix).stars(4).create();

		assertThat(save(token, hotel.id())).hasStatus(HttpStatus.NO_CONTENT);
		String savedAt = JsonPath.read(body(list(token, "")), "$.content[0].savedAt");
		assertThat(save(token, hotel.id())).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult result = list(token, "");
		assertThat(slugs(result)).containsExactly(hotel.slug());
		assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.content[0].name").isEqualTo(hotel.name());
		assertThat(result).bodyJson().extractingPath("$.content[0].starRating").isEqualTo(4);
		assertThat(result).bodyJson().extractingPath("$.content[0].available").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.content[0].savedAt").isEqualTo(savedAt);
	}

	@Test
	void listsNewestFirstInPages() {
		CreatedHotel a = catalog.hotel("A " + suffix).create();
		CreatedHotel b = catalog.hotel("B " + suffix).create();
		CreatedHotel c = catalog.hotel("C " + suffix).create();
		save(token, a.id());
		save(token, b.id());
		save(token, c.id());

		MvcTestResult first = list(token, "?size=2");
		assertThat(slugs(first)).containsExactly(c.slug(), b.slug());
		assertThat(first).bodyJson().extractingPath("$.page.totalElements").isEqualTo(3);
		assertThat(slugs(list(token, "?size=2&page=1"))).containsExactly(a.slug());
		assertThat(slugs(list(token, "?sort=name,asc"))).containsExactly(c.slug(), b.slug(), a.slug());
		// A page past the end is empty but still counts every entry.
		assertThat(list(token, "?size=2&page=5")).bodyJson().extractingPath("$.page.totalElements").isEqualTo(3);
	}

	@Test
	void removingIsIdempotent() {
		CreatedHotel saved = catalog.hotel("Removed " + suffix).create();
		CreatedHotel neverSaved = catalog.hotel("Never " + suffix).create();
		save(token, saved.id());

		assertThat(remove(token, saved.id())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(slugs(list(token, ""))).isEmpty();
		assertThat(remove(token, saved.id())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(remove(token, neverSaved.id())).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void onlyListedHotelsCanBeSaved() {
		CreatedHotel inactive = catalog.hotel("Inactive " + suffix).status("INACTIVE").create();
		CreatedHotel deleted = catalog.hotel("Deleted " + suffix).create();
		softDelete(deleted.id());

		MvcTestResult unknown = save(token, Long.MAX_VALUE);
		assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(unknown.getResponse().getContentType()).isEqualTo("application/problem+json");
		assertThat(unknown).bodyJson().extractingPath("$.detail").isEqualTo("Hotel %d not found".formatted(Long.MAX_VALUE));
		assertThat(save(token, inactive.id())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(save(token, deleted.id())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.put().uri("/api/wishlist/abc").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(slugs(list(token, ""))).isEmpty();
	}

	@Test
	void delistedHotelsStayAsUnavailable() {
		CreatedHotel paused = catalog.hotel("Paused " + suffix).create();
		CreatedHotel deleted = catalog.hotel("Gone " + suffix).create();
		save(token, paused.id());
		save(token, deleted.id());

		catalog.hotelStatus(paused.id(), "INACTIVE");
		softDelete(deleted.id());
		MvcTestResult delisted = list(token, "");
		assertThat(slugs(delisted)).containsExactly(deleted.slug(), paused.slug());
		assertThat(availability(delisted)).containsExactly(false, false);
		assertThat(delisted).bodyJson().extractingPath("$.content[1].name").isEqualTo(paused.name());

		catalog.hotelStatus(paused.id(), "ACTIVE");
		assertThat(availability(list(token, ""))).containsExactly(false, true);

		assertThat(remove(token, deleted.id())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(slugs(list(token, ""))).containsExactly(paused.slug());
	}

	@Test
	void eachUserHasTheirOwnWishlist() {
		CreatedHotel hotel = catalog.hotel("Mine " + suffix).create();
		String other = userToken(mvc);
		save(token, hotel.id());

		assertThat(slugs(list(other, ""))).isEmpty();
		assertThat(remove(other, hotel.id())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(slugs(list(token, ""))).containsExactly(hotel.slug());
	}

	@Test
	void requiresSignIn() {
		CreatedHotel hotel = catalog.hotel("Anonymous " + suffix).create();

		assertThat(mvc.get().uri("/api/wishlist")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put().uri("/api/wishlist/{id}", hotel.id())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete().uri("/api/wishlist/{id}", hotel.id())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult save(String token, long hotelId) {
		return mvc.put().uri("/api/wishlist/{id}", hotelId).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
	}

	private MvcTestResult remove(String token, long hotelId) {
		return mvc.delete().uri("/api/wishlist/{id}", hotelId).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
	}

	private MvcTestResult list(String token, String query) {
		return mvc.get().uri("/api/wishlist" + query).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
	}

	private void softDelete(long hotelId) {
		jdbc.sql("update hotels set deleted_at = now() where id = :id").param("id", hotelId).update();
	}

	private static List<String> slugs(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.content[*].slug");
	}

	private static List<Boolean> availability(MvcTestResult result) {
		assertThat(result).hasStatusOk();
		return JsonPath.read(body(result), "$.content[*].available");
	}

}
