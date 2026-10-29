package com.company.project.hotel.controller;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static com.company.project.support.TestAuth.body;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
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

/** The hotel page's overview, gallery, facilities and policies (C-HOTEL-01/02/03), opened without a token. */
@IntegrationTest
class HotelControllerTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DestinationSyncService destinationSync;

	CatalogFixtures catalog;

	String suffix;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
	}

	@Test
	void showsTheHotelWithItsAddressInOneLine() {
		CreatedHotel hotel = catalog.hotel("Overview " + suffix)
			.type("resort")
			.stars(5)
			.description("Beachfront resort " + suffix)
			.userRating("9.2", 873)
			.googleRating("4.7")
			.distance(4200)
			.ward("Phước Mỹ " + suffix)
			.district("Sơn Trà " + suffix)
			.city("Đà Nẵng " + suffix)
			.province("Đà Nẵng " + suffix)
			.coordinates("16.064000", "108.246800")
			.create();
		destinationSync.sync();
		// Other tests may rename countries, so read the name the page must show.
		String vietnam = jdbc.sql("""
				select name from destinations where type = 'COUNTRY' and country_code = 'VN' and deleted_at is null""")
			.query(String.class)
			.single();

		MvcTestResult result = detail(hotel.slug());

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.id").isEqualTo((int) hotel.id());
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(hotel.name());
		assertThat(result).bodyJson().extractingPath("$.accommodationType").isEqualTo("resort");
		assertThat(result).bodyJson().extractingPath("$.accommodationTypeName").isEqualTo("Resort");
		assertThat(result).bodyJson().extractingPath("$.starRating").isEqualTo(5);
		assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("Beachfront resort " + suffix);
		assertThat(result).bodyJson().extractingPath("$.userRatingAvg").isEqualTo(9.2);
		assertThat(result).bodyJson().extractingPath("$.userRatingCount").isEqualTo(873);
		assertThat(result).bodyJson().extractingPath("$.googleRating").isEqualTo(4.7);
		assertThat(result).bodyJson().extractingPath("$.distanceToCenterM").isEqualTo(4200);
		assertThat(result).bodyJson().extractingPath("$.address.countryName").isEqualTo(vietnam);
		assertThat(result).bodyJson()
			.extractingPath("$.address.formatted")
			.isEqualTo("1 Test Street, Phước Mỹ %1$s, Sơn Trà %1$s, Đà Nẵng %1$s, %2$s".formatted(suffix, vietnam));
		assertThat(result).bodyJson().extractingPath("$.address.latitude").isEqualTo(16.064);
		assertThat(result).bodyJson().extractingPath("$.address.longitude").isEqualTo(108.2468);
	}

	@Test
	void usesTheCountryCodeWhileTheCountryHasNoName() {
		// A user-assigned code no other test uses; the JDK has no name for it, so even a sync leaves the code.
		CreatedHotel hotel = catalog.hotel("Unnamed country " + suffix).country("QM").city("Town " + suffix).create();

		MvcTestResult result = detail(hotel.slug());

		assertThat(result).bodyJson()
			.extractingPath("$.address.formatted")
			.isEqualTo("1 Test Street, Town %s, QM".formatted(suffix));
		assertThat(result).bodyJson().extractingPath("$.address.latitude").isNull();
	}

	@Test
	void galleryListsHotelPhotosCoverFirstThenRoomPhotosOnSale() {
		CreatedHotel hotel = catalog.hotel("Gallery " + suffix).create();
		long onSale = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		long withdrawn = catalog.bareRoomType(hotel.id(), 2, 0, 2, 3);
		catalog.roomTypeStatus(withdrawn, "INACTIVE");
		catalog.roomPhoto(hotel.id(), onSale, "https://img.test/room-" + suffix);
		catalog.roomPhoto(hotel.id(), withdrawn, "https://img.test/withdrawn-" + suffix);
		catalog.photo(hotel.id(), "https://img.test/pool-" + suffix, "pool", "Rooftop pool", false, 2);
		catalog.photo(hotel.id(), "https://img.test/lobby-" + suffix, "lobby", null, false, 1);
		catalog.photo(hotel.id(), "https://img.test/cover-" + suffix, "exterior", "Front", true, 9);

		MvcTestResult result = detail(hotel.slug());

		List<String> urls = JsonPath.read(body(result), "$.photos[*].url");
		assertThat(urls).containsExactly("https://img.test/cover-" + suffix, "https://img.test/lobby-" + suffix,
				"https://img.test/pool-" + suffix, "https://img.test/room-" + suffix);
		List<String> categories = JsonPath.read(body(result), "$.photos[*].category");
		assertThat(categories).containsExactly("exterior", "lobby", "pool", "room");
		assertThat(result).bodyJson().extractingPath("$.photos[2].caption").isEqualTo("Rooftop pool");
		assertThat(result).bodyJson().extractingPath("$.photos[0].roomTypeId").isNull();
		assertThat(result).bodyJson().extractingPath("$.photos[3].roomTypeId").isEqualTo((int) onSale);
	}

	@Test
	void groupsFacilitiesAndMarksThePaidOnes() {
		CreatedHotel hotel = catalog.hotel("Facilities " + suffix).create();
		catalog.hotelAmenity(hotel.id(), "spa");
		catalog.hotelAmenity(hotel.id(), "parking", false);
		catalog.hotelAmenity(hotel.id(), "wifi");
		catalog.hotelAmenity(hotel.id(), "pool");
		catalog.hotelAmenity(hotel.id(), "bar");
		// A room feature linked to the hotel is not one of its facilities.
		catalog.hotelAmenity(hotel.id(), "balcony");

		MvcTestResult result = detail(hotel.slug());

		List<String> groups = JsonPath.read(body(result), "$.facilities[*].name");
		assertThat(groups).containsExactly("Food & drink", "General", "Wellness");
		List<String> general = JsonPath.read(body(result), "$.facilities[1].facilities[*].code");
		assertThat(general).containsExactly("wifi", "parking");
		List<Boolean> generalFree = JsonPath.read(body(result), "$.facilities[1].facilities[*].free");
		assertThat(generalFree).containsExactly(true, false);
		List<String> wellness = JsonPath.read(body(result), "$.facilities[2].facilities[*].name");
		assertThat(wellness).containsExactly("Spa", "Swimming pool");
	}

	@Test
	void showsCheckInTimesAndPoliciesInTheHotelsOrder() {
		CreatedHotel hotel = catalog.hotel("Policies " + suffix)
			.checkInUntil(LocalTime.of(22, 0))
			.minCheckInAge(21)
			.create();
		catalog.hotelPolicy(hotel.id(), "PETS", "Pets are not allowed.", 2);
		catalog.hotelPolicy(hotel.id(), "DEPOSIT", "A photo ID is required.", 3);
		catalog.hotelPolicy(hotel.id(), "CHILDREN", "Children are welcome.", 1);

		MvcTestResult result = detail(hotel.slug());

		assertThat(result).bodyJson().extractingPath("$.policies.checkInFrom").isEqualTo("14:00:00");
		assertThat(result).bodyJson().extractingPath("$.policies.checkInUntil").isEqualTo("22:00:00");
		assertThat(result).bodyJson().extractingPath("$.policies.checkOutUntil").isEqualTo("12:00:00");
		assertThat(result).bodyJson().extractingPath("$.policies.minCheckInAge").isEqualTo(21);
		List<String> types = JsonPath.read(body(result), "$.policies.rules[*].type");
		assertThat(types).containsExactly("CHILDREN", "PETS", "DEPOSIT");
		assertThat(result).bodyJson().extractingPath("$.policies.rules[1].description").isEqualTo("Pets are not allowed.");
	}

	@Test
	void aHotelWithoutExtrasHasEmptyLists() {
		CreatedHotel hotel = catalog.hotel("Bare " + suffix).create();

		MvcTestResult result = detail(hotel.slug());

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.photos").asArray().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.facilities").asArray().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.policies.rules").asArray().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.policies.checkInUntil").isNull();
		assertThat(result).bodyJson().extractingPath("$.policies.minCheckInAge").isEqualTo(18);
	}

	@Test
	void onlyListedHotelsCanBeOpened() {
		CreatedHotel inactive = catalog.hotel("Inactive " + suffix).status("INACTIVE").create();
		CreatedHotel deleted = catalog.hotel("Deleted " + suffix).create();
		jdbc.sql("update hotels set deleted_at = now() where id = :id").param("id", deleted.id()).update();

		MvcTestResult unknown = detail("no-such-hotel-" + suffix);
		assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(unknown.getResponse().getContentType()).isEqualTo("application/problem+json");
		assertThat(detail(inactive.slug())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(detail(deleted.slug())).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult detail(String slug) {
		return mvc.get().uri("/api/hotels/{slug}", slug).exchange();
	}

}
