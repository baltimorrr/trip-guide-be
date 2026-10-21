package com.company.project.product.controller;

import static com.company.project.support.TestAuth.adminToken;
import static com.company.project.support.TestAuth.bearer;
import static com.company.project.support.TestAuth.body;
import static com.company.project.support.TestAuth.userToken;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.project.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Tests assert on the products they create, never on total counts, because the database is shared. */
@IntegrationTest
class ProductControllerTest {

	static final String VALID_PRODUCT = """
			{"name": "Coffee beans", "description": "1kg bag", "price": 19.99}""";

	@Autowired
	MockMvcTester mvc;

	String adminToken;

	String userToken;

	@BeforeEach
	void obtainTokens() {
		adminToken = adminToken(mvc);
		userToken = userToken(mvc);
	}

	@Test
	void listingRequiresAuthentication() {
		assertThat(mvc.get().uri("/api/products")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void userCanListAndReadProducts() {
		long id = createProduct(VALID_PRODUCT);

		MvcTestResult list = mvc.get().uri("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(userToken)).exchange();
		assertThat(list).hasStatusOk();
		assertThat(list).bodyJson().hasPath("$.content");
		assertThat(list).bodyJson().extractingPath("$.page.size").isEqualTo(20);
		assertThat(list).bodyJson().extractingPath("$.page.totalElements").asNumber().satisfies(
				total -> assertThat(total.longValue()).isPositive());

		assertThat(mvc.get().uri("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.name")
				.isEqualTo("Coffee beans");
	}

	@Test
	void userCannotModifyProducts() {
		long id = createProduct(VALID_PRODUCT);

		MvcTestResult create = mvc.post().uri("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(userToken))
				.contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT).exchange();
		MvcTestResult update = mvc.put().uri("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(userToken))
				.contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT).exchange();
		MvcTestResult delete = mvc.delete().uri("/api/products/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, bearer(userToken)).exchange();

		for (MvcTestResult result : new MvcTestResult[] { create, update, delete }) {
			// 403 problem+json, not a 500 from the catch-all handler.
			assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json");
			assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Access denied");
		}
	}

	@Test
	void adminCanCreateUpdateAndDelete() {
		MvcTestResult created = mvc.post().uri("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT).exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);
		long id = idOf(created);
		assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/products/" + id);
		assertThat(created).bodyJson().extractingPath("$.price").isEqualTo(19.99);

		String changed = """
				{"name": "Coffee beans (decaf)", "description": null, "price": 21.50}""";
		MvcTestResult updated = mvc.put().uri("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON).content(changed).exchange();
		assertThat(updated).hasStatusOk();
		assertThat(updated).bodyJson().extractingPath("$.name").isEqualTo("Coffee beans (decaf)");
		assertThat(updated).bodyJson().extractingPath("$.price").isEqualTo(21.5);

		assertThat(mvc.delete().uri("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
				.hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult gone = mvc.get().uri("/api/products/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).exchange();
		assertThat(gone).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(gone).bodyJson().extractingPath("$.detail").isEqualTo("Product " + id + " not found");
	}

	@Test
	void writeResponseTimestampsMatchWhatIsStored() {
		MvcTestResult created = mvc.post().uri("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT).exchange();
		MvcTestResult read = mvc.get().uri("/api/products/{id}", idOf(created))
				.header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).exchange();

		// The database keeps microseconds; a write response must not show more precision than a later read.
		for (String field : new String[] { "createdAt", "updatedAt" }) {
			String written = JsonPath.read(body(created), "$." + field);
			String stored = JsonPath.read(body(read), "$." + field);
			assertThat(written).isEqualTo(stored);
		}
	}

	@Test
	void changingMissingProductReturnsNotFound() {
		assertThat(mvc.put().uri("/api/products/999999999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT))
				.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.delete().uri("/api/products/999999999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void invalidProductReturnsFieldErrors() {
		MvcTestResult blankAndNegative = postAsAdmin("""
				{"name": "", "price": -1}""");
		assertThat(blankAndNegative).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(blankAndNegative).bodyJson().extractingPath("$.errors[*].field").asArray().contains("name", "price");

		MvcTestResult tooManyDecimals = postAsAdmin("""
				{"name": "Tea", "price": 1.234}""");
		assertThat(tooManyDecimals).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(tooManyDecimals).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("price");
	}

	@Test
	void unknownSortPropertyIsBadRequest() {
		MvcTestResult result = mvc.get().uri("/api/products?sort=nope").header(HttpHeaders.AUTHORIZATION, bearer(userToken)).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Unknown sort property: nope");
	}

	@Test
	void pageSizeIsCapped() {
		assertThat(mvc.get().uri("/api/products?size=1000").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.page.size")
				.isEqualTo(100);
	}

	private long createProduct(String json) {
		MvcTestResult result = postAsAdmin(json);
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return idOf(result);
	}

	private MvcTestResult postAsAdmin(String json) {
		return mvc.post().uri("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private static long idOf(MvcTestResult result) {
		Number id = JsonPath.read(body(result), "$.id");
		return id.longValue();
	}

}
