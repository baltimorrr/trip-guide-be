package com.company.project.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import com.company.project.TestcontainersConfiguration;
import com.company.project.common.config.JpaAuditingConfig;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * JPA slice test: only repositories, entities and Flyway are loaded, against a real
 * PostgreSQL container. Each test runs in a transaction that is rolled back afterwards.
 * Slices skip scanned configuration, so auditing (audit timestamps) is imported explicitly.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JpaAuditingConfig.class })
@ActiveProfiles("test")
class UserRepositoryTest {

	@Autowired
	UserRepository users;

	@Autowired
	EntityManager entityManager;

	@Test
	void savesUserWithRolesAndAuditColumns() {
		users.saveAndFlush(User.create("jane@example.com", "{noop}secret", "Jane", Set.of(Role.USER, Role.ADMIN)));
		entityManager.clear(); // force a real reload from the database

		User found = users.findByEmail("jane@example.com").orElseThrow();

		assertThat(found.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
		assertThat(found.getVersion()).isZero();
		assertThat(users.existsByEmail("jane@example.com")).isTrue();
	}

	@Test
	void emailIsStoredNormalized() {
		users.saveAndFlush(User.create("  Mixed.Case@Example.COM ", "{noop}secret", "Mixed", Set.of(Role.USER)));

		assertThat(users.findByEmail("mixed.case@example.com")).isPresent();
	}

	@Test
	void duplicateEmailViolatesUniqueConstraint() {
		users.saveAndFlush(User.create("dup@example.com", "{noop}secret", "First", Set.of(Role.USER)));

		assertThatThrownBy(() -> users.saveAndFlush(User.create("dup@example.com", "{noop}secret", "Second", Set.of(Role.USER))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
