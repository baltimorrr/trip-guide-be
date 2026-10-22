package com.company.project.destination.controller;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.adminToken;
import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.userToken;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.company.project.destination.services.DestinationSyncService;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Admin curation of destinations. Each test works on its own city. */
@IntegrationTest
class AdminDestinationControllerTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DestinationSyncService syncService;

	CatalogFixtures catalog;

	String suffix;

	String adminToken;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
		adminToken = adminToken(mvc);
	}

	@Test
	void onlyAdminsMayCurate() {
		assertThat(mvc.get().uri("/api/admin/destinations")).hasStatus(HttpStatus.UNAUTHORIZED);
		String userToken = userToken(mvc);
		assertThat(mvc.get().uri("/api/admin/destinations").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/admin/destinations/sync").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void listFindsDestinationsByNamePrefix() {
		catalog.hotel("Hotel " + suffix).city("Quy Nhơn " + suffix).create();
		syncService.sync();

		MvcTestResult result = admin(mvc.get().uri("/api/admin/destinations").param("q", "QUY NHON " + suffix));

		assertThat(result).hasStatusOk();
		List<Map<String, Object>> content = JsonPath.read(body(result), "$.content");
		assertThat(content).singleElement().satisfies(found -> assertThat(found)
			.containsEntry("slug", "vn-quy-nhon-" + suffix)
			.containsEntry("type", "CITY")
			.containsEntry("source", "SYNC")
			.containsEntry("hotelCount", 1));
	}

	@Test
	void updateSetsWhatAdminsOwnAndSyncKeepsIt() {
		catalog.hotel("Hotel " + suffix).city("Nha Trang " + suffix).create();
		syncService.sync();
		long id = destinationId("vn-nha-trang-" + suffix);

		MvcTestResult updated = admin(mvc.put().uri("/api/admin/destinations/{id}", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Nha Trang Bay %s", "imageUrl": "https://img.test/nha-trang.jpg", "featuredRank": 5}"""
				.formatted(suffix)));

		assertThat(updated).hasStatusOk();
		assertThat(updated).bodyJson().extractingPath("$.name").isEqualTo("Nha Trang Bay " + suffix);
		assertThat(updated).bodyJson().extractingPath("$.featuredRank").isEqualTo(5);
		assertThat(updated).bodyJson().extractingPath("$.hotelCount").isEqualTo(1);

		// The generated normalized name follows the new display name, so autocomplete finds it.
		assertThat(mvc.get().uri("/api/search/suggestions").param("q", "bay " + suffix))
			.bodyJson()
			.extractingPath("$.destinations[0].slug")
			.isEqualTo("vn-nha-trang-" + suffix);

		syncService.sync();
		assertThat(jdbc.sql("select name, image_url, featured_rank, hotel_count from destinations where id = :id")
			.param("id", id)
			.query()
			.singleRow())
			.containsEntry("name", "Nha Trang Bay " + suffix)
			.containsEntry("image_url", "https://img.test/nha-trang.jpg")
			.containsEntry("featured_rank", 5) // the driver reads smallint as Integer
			.containsEntry("hotel_count", 1);
	}

	@Test
	void renamedCountryShowsInThePlacesInside() {
		// A user-assigned ISO code that no other test uses: every test shares the country rows.
		catalog.hotel("Hotel " + suffix).country("XQ").city("Far City " + suffix).create();
		syncService.sync();

		assertThat(admin(mvc.put().uri("/api/admin/destinations/{id}", destinationId("xq"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Farland %s"}""".formatted(suffix)))).hasStatusOk();

		assertThat(mvc.get().uri("/api/search/suggestions").param("q", "far city " + suffix))
			.bodyJson()
			.extractingPath("$.destinations[0].context")
			.isEqualTo("Farland " + suffix);
		assertThat(mvc.get().uri("/api/search/suggestions").param("q", "farland " + suffix))
			.bodyJson()
			.extractingPath("$.destinations[0].slug")
			.isEqualTo("xq");
	}

	@Test
	void updateValidatesInput() {
		catalog.hotel("Hotel " + suffix).city("Valid City " + suffix).create();
		syncService.sync();
		long id = destinationId("vn-valid-city-" + suffix);

		MvcTestResult result = admin(mvc.put().uri("/api/admin/destinations/{id}", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": " ", "imageUrl": "ftp://img.test/x.jpg", "featuredRank": 0}"""));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(JsonPath.<List<String>>read(body(result), "$.errors[*].field"))
			.containsExactlyInAnyOrder("name", "imageUrl", "featuredRank");
		assertThat(admin(mvc.put().uri("/api/admin/destinations/{id}", Long.MAX_VALUE)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Anything"}"""))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void syncRunsOnDemand() {
		catalog.hotel("Hotel " + suffix).city("Fresh City " + suffix).create();

		MvcTestResult result = admin(mvc.post().uri("/api/admin/destinations/sync"));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.created").asNumber()
			.satisfies(created -> assertThat(created.intValue()).isPositive());
		assertThat(destinationId("vn-fresh-city-" + suffix)).isPositive();
	}

	private MvcTestResult admin(MockMvcTester.MockMvcRequestBuilder request) {
		return request.header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).exchange();
	}

	private long destinationId(String slug) {
		return jdbc.sql("select id from destinations where slug = :slug").param("slug", slug).query(Long.class).single();
	}

}
