package com.company.project.destination.repository;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.project.destination.model.Destination;
import com.company.project.destination.model.DestinationType;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The catalog schema behind destinations: norm(), the generated name and keys, and the place-key rules. */
@IntegrationTest
class DestinationRepositoryTest {

	@Autowired
	DestinationRepository destinations;

	@Autowired
	JdbcClient jdbc;

	@Test
	void normIgnoresAccentsCaseAndExtraSpaces() {
		assertThat(norm("  Đà  Nẵng ")).isEqualTo("da nang");
		assertThat(norm("HỒ CHÍ MINH")).isEqualTo("ho chi minh");
		assertThat(norm(null)).isNull();
	}

	@Test
	void normalizedNameIsGeneratedFromName() {
		String suffix = uniqueSuffix();
		String slug = insertCity("Đà Nẵng " + suffix, "Đà Nẵng " + suffix);

		Destination found = destinations.findBySlugAndDeletedAtIsNull(slug).orElseThrow();

		assertThat(found.getType()).isEqualTo(DestinationType.CITY);
		assertThat(found.getName()).isEqualTo("Đà Nẵng " + suffix);
		assertThat(found.getNormalizedName()).isEqualTo("da nang " + suffix);
	}

	@Test
	void keysAreGeneratedFromTheSpellings() {
		String suffix = uniqueSuffix();
		String slug = "test-" + suffix;
		jdbc.sql("""
				insert into destinations (type, name, slug, country_code, state_province, city, district, ward,
				                          created_at, updated_at)
				values ('WARD', :ward, :slug, 'VN', :city, :city, :district, :ward, now(), now())""")
			.param("ward", "Thạch Thang " + suffix)
			.param("slug", slug)
			.param("city", "Đà Nẵng " + suffix)
			.param("district", " Hải  Châu " + suffix)
			.update();

		assertThat(jdbc.sql("""
				select province_key, city_key, district_key, ward_key from destinations
				where slug = :slug""")
			.param("slug", slug)
			.query()
			.singleRow())
			.containsEntry("province_key", "da nang " + suffix)
			.containsEntry("city_key", "da nang " + suffix)
			.containsEntry("district_key", "hai chau " + suffix)
			.containsEntry("ward_key", "thach thang " + suffix);
	}

	@Test
	void spellingsThatNormalizeAlikeAreOnePlace() {
		String suffix = uniqueSuffix();
		insertCity("First", "Đà Nẵng " + suffix);

		assertThatThrownBy(() -> insertCity("Second", " DA  NANG " + suffix))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void cityNeedsACityKey() {
		assertThatThrownBy(() -> insertCity("No key", " ")).isInstanceOf(DataIntegrityViolationException.class);
	}

	private String norm(String value) {
		return jdbc.sql("select norm(:value)").param("value", value).query(String.class).optional().orElse(null);
	}

	private String insertCity(String name, String city) {
		String slug = "test-" + uniqueSuffix();
		jdbc.sql("""
				insert into destinations (type, name, slug, country_code, city, created_at, updated_at)
				values ('CITY', :name, :slug, 'VN', :city, now(), now())""")
			.param("name", name)
			.param("slug", slug)
			.param("city", city)
			.update();
		return slug;
	}

}
