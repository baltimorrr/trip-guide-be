package com.company.project.destination.services.impl;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.company.project.destination.services.DestinationSyncService;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The sync is SQL, so it is tested against the real database. Every test uses its own place names and
 * reads back only the rows for those places; the sync itself always runs over the whole table.
 */
@IntegrationTest
class DestinationSyncServiceImplTest {

	@Autowired
	DestinationSyncService syncService;

	@Autowired
	JdbcClient jdbc;

	CatalogFixtures catalog;

	String suffix;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		suffix = uniqueSuffix();
	}

	@Test
	void createsEveryLevelOfAListedHotelsAddress() {
		catalog.hotel("Hotel " + suffix)
			.city("Đà Nẵng " + suffix)
			.district("Hải Châu " + suffix)
			.ward("Thạch Thang " + suffix)
			.create();

		syncService.sync();

		Place city = place("CITY", "", "da nang " + suffix, "", "").orElseThrow();
		assertThat(city.name()).isEqualTo("Đà Nẵng " + suffix);
		assertThat(city.normalizedName()).isEqualTo("da nang " + suffix);
		assertThat(city.slug()).isEqualTo("vn-da-nang-" + suffix);
		assertThat(city.hotelCount()).isEqualTo(1);
		assertThat(place("DISTRICT", "", "da nang " + suffix, "hai chau " + suffix, "").orElseThrow().slug())
			.isEqualTo("vn-da-nang-%s-hai-chau-%s".formatted(suffix, suffix));
		Place ward = place("WARD", "", "da nang " + suffix, "hai chau " + suffix, "thach thang " + suffix).orElseThrow();
		assertThat(ward.slug()).isEqualTo("vn-da-nang-%s-hai-chau-%s-thach-thang-%s".formatted(suffix, suffix, suffix));
		assertThat(ward.hotelCount()).isEqualTo(1);
		Place country = place("COUNTRY", "", "", "", "").orElseThrow();
		assertThat(country.name()).isEqualTo("Vietnam");
		assertThat(country.slug()).isEqualTo("vn");
		assertThat(country.hotelCount()).isPositive();
	}

	@Test
	void ignoresHotelsThatAreNotListed() {
		catalog.hotel("Draft " + suffix).city("Draft City " + suffix).status("DRAFT").create();
		CreatedHotel deleted = catalog.hotel("Deleted " + suffix).city("Deleted City " + suffix).create();
		jdbc.sql("update hotels set deleted_at = now() where id = :id").param("id", deleted.id()).update();

		syncService.sync();

		assertThat(place("CITY", "", "draft city " + suffix, "", "")).isEmpty();
		assertThat(place("CITY", "", "deleted city " + suffix, "", "")).isEmpty();
	}

	@Test
	void spellingsThatNormalizeAlikeShareOnePlace() {
		catalog.hotel("One " + suffix).city("Đà Nẵng " + suffix).create();
		catalog.hotel("Two " + suffix).city(" da  nang " + suffix.toUpperCase()).create();

		syncService.sync();

		assertThat(place("CITY", "", "da nang " + suffix, "", "").orElseThrow().hotelCount()).isEqualTo(2);
	}

	@Test
	void storesTheMostCommonSpellingOfEachLevel() {
		catalog.hotel("One " + suffix).province("Đà Nẵng " + suffix).city("Đà Nẵng " + suffix)
			.district("Hải Châu " + suffix).create();
		catalog.hotel("Two " + suffix).province("Đà Nẵng " + suffix).city("Đà Nẵng " + suffix)
			.district("Hải Châu " + suffix).create();
		catalog.hotel("Three " + suffix).province("da nang " + suffix).city("Da Nang " + suffix)
			.district("Hai Chau " + suffix).ward("Thạch Thang " + suffix).create();

		syncService.sync();

		assertThat(spellings("vn-da-nang-" + suffix))
			.containsEntry("name", "Đà Nẵng " + suffix)
			.containsEntry("state_province", "Đà Nẵng " + suffix)
			.containsEntry("city", "Đà Nẵng " + suffix)
			.containsEntry("district", null)
			.containsEntry("ward", null);
		// Only the third hotel is in the ward, but the ward reuses the spellings its city and district chose.
		assertThat(spellings("vn-da-nang-%s-hai-chau-%s-thach-thang-%s".formatted(suffix, suffix, suffix)))
			.containsEntry("state_province", "Đà Nẵng " + suffix)
			.containsEntry("city", "Đà Nẵng " + suffix)
			.containsEntry("district", "Hải Châu " + suffix)
			.containsEntry("ward", "Thạch Thang " + suffix);
	}

	@Test
	void refreshesASpellingThatIsNoLongerTheMostCommon() {
		catalog.hotel("One " + suffix).city("Da Nang " + suffix).create();
		syncService.sync();
		catalog.hotel("Two " + suffix).city("Đà Nẵng " + suffix).create();
		catalog.hotel("Three " + suffix).city("Đà Nẵng " + suffix).create();

		syncService.sync();

		assertThat(spellings("vn-da-nang-" + suffix))
			.containsEntry("city", "Đà Nẵng " + suffix)
			.containsEntry("name", "Da Nang " + suffix); // the display name belongs to admins once created
	}

	@Test
	void placeThatLosesItsHotelsIsKeptWithZeroCount() {
		CreatedHotel hotel = catalog.hotel("Hotel " + suffix).city("Closing " + suffix).create();
		syncService.sync();

		catalog.hotelStatus(hotel.id(), "INACTIVE");
		syncService.sync();

		assertThat(place("CITY", "", "closing " + suffix, "", "").orElseThrow().hotelCount()).isZero();
	}

	@Test
	void neverOverwritesWhatAdminsOwn() {
		catalog.hotel("Hotel " + suffix).city("Hội An " + suffix).create();
		syncService.sync();
		jdbc.sql("""
				update destinations set name = 'Hoi An by admin', image_url = 'https://img.test/hoi-an.jpg',
				       featured_rank = 3
				where type = 'CITY' and city_key = :key""")
			.param("key", "hoi an " + suffix)
			.update();

		syncService.sync();

		Place city = place("CITY", "", "hoi an " + suffix, "", "").orElseThrow();
		assertThat(city.name()).isEqualTo("Hoi An by admin");
		assertThat(city.imageUrl()).isEqualTo("https://img.test/hoi-an.jpg");
		assertThat(city.featuredRank()).isEqualTo((short) 3);
		assertThat(city.hotelCount()).isEqualTo(1);
	}

	@Test
	void runningAgainCreatesNothingNew() {
		catalog.hotel("Hotel " + suffix).city("Repeat " + suffix).district("Center " + suffix).create();
		syncService.sync();

		syncService.sync();

		assertThat(jdbc.sql("select count(*) from destinations where city_key = :key")
			.param("key", "repeat " + suffix)
			.query(Integer.class)
			.single()).isEqualTo(2); // the city and its district
	}

	@Test
	void sameCityNameInTwoProvincesGetsTwoSlugs() {
		catalog.hotel("North " + suffix).province("North " + suffix).city("Twin " + suffix).create();
		catalog.hotel("South " + suffix).province("South " + suffix).city("Twin " + suffix).create();

		syncService.sync();

		List<String> slugs = jdbc.sql("select slug from destinations where type = 'CITY' and city_key = :key")
			.param("key", "twin " + suffix)
			.query(String.class)
			.list();
		assertThat(slugs).hasSize(2).doesNotHaveDuplicates().allMatch(slug -> slug.startsWith("vn-twin-" + suffix));
	}

	@Test
	void namesNewCountriesInEnglish() {
		catalog.hotel("Hotel " + suffix).country("IS").city("Reykjavik " + suffix).create();

		syncService.sync();

		Place country = jdbc.sql("""
				select type, name, normalized_name, slug, hotel_count, image_url, featured_rank from destinations
				where type = 'COUNTRY' and country_code = 'IS' and deleted_at is null""")
			.query(Place.class)
			.single();
		assertThat(country.name()).isEqualTo("Iceland");
		assertThat(country.normalizedName()).isEqualTo("iceland");
	}

	/** A Vietnamese place by its keys. */
	private Optional<Place> place(String type, String provinceKey, String cityKey, String districtKey, String wardKey) {
		return jdbc.sql("""
				select type, name, normalized_name, slug, hotel_count, image_url, featured_rank from destinations
				where deleted_at is null and type = :type and country_code = 'VN' and province_key = :provinceKey
				  and city_key = :cityKey and district_key = :districtKey and ward_key = :wardKey""")
			.param("type", type)
			.param("provinceKey", provinceKey)
			.param("cityKey", cityKey)
			.param("districtKey", districtKey)
			.param("wardKey", wardKey)
			.query(Place.class)
			.optional();
	}

	private Map<String, Object> spellings(String slug) {
		return jdbc.sql("""
				select name, state_province, city, district, ward from destinations
				where slug = :slug""")
			.param("slug", slug)
			.query()
			.singleRow();
	}

	record Place(String type, String name, String normalizedName, String slug, int hotelCount, String imageUrl,
			Short featuredRank) {
	}

}
