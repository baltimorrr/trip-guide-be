package com.company.project.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

	private static final Duration WINDOW = Duration.ofMinutes(15);

	private static final Duration LOCK = Duration.ofMinutes(15);

	@Autowired
	UserRepository users;

	@Autowired
	EntityManager entityManager;

	@Test
	void savesUserWithRolesAndAuditColumns() {
		users.saveAndFlush(User.create("jane@example.com", "{noop}secret", "Jane", null, Set.of(Role.USER, Role.ADMIN), null));
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
		users.saveAndFlush(User.create("  Mixed.Case@Example.COM ", "{noop}secret", "Mixed", null, Set.of(Role.USER), null));

		assertThat(users.findByEmail("mixed.case@example.com")).isPresent();
	}

	@Test
	void duplicateEmailViolatesUniqueConstraint() {
		users.saveAndFlush(User.create("dup@example.com", "{noop}secret", "First", null, Set.of(Role.USER), null));

		assertThatThrownBy(() -> users.saveAndFlush(User.create("dup@example.com", "{noop}secret", "Second", null, Set.of(Role.USER), null)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void fifthFailureInsideTheWindowLocksTheAccount() {
		Long id = users.saveAndFlush(User.create("lock@example.com", "{noop}secret", "Lock", null, Set.of(Role.USER), null)).getId();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		for (int attempt = 1; attempt <= 4; attempt++) {
			recordFailure("lock@example.com", now);
		}
		User afterFour = reload(id);
		assertThat(afterFour.getFailedLoginCount()).isEqualTo((short) 4);
		assertThat(afterFour.getFirstFailedLoginAt()).isEqualTo(now);
		assertThat(afterFour.isLocked(now)).isFalse();

		recordFailure("lock@example.com", now);

		User afterFive = reload(id);
		assertThat(afterFive.getLockedUntil()).isEqualTo(now.plus(LOCK));
		assertThat(afterFive.isLocked(now)).isTrue();
		assertThat(afterFive.getVersion()).isEqualTo(5);
	}

	@Test
	void failureAfterTheWindowStartsCountingAgain() {
		Long id = users.saveAndFlush(User.create("window@example.com", "{noop}secret", "Window", null, Set.of(Role.USER), null)).getId();
		Instant start = Instant.now().truncatedTo(ChronoUnit.MICROS);
		for (int attempt = 1; attempt <= 4; attempt++) {
			recordFailure("window@example.com", start);
		}

		Instant later = start.plus(WINDOW).plusSeconds(1);
		recordFailure("window@example.com", later);

		User user = reload(id);
		assertThat(user.getFailedLoginCount()).isEqualTo((short) 1);
		assertThat(user.getFirstFailedLoginAt()).isEqualTo(later);
		assertThat(user.getLockedUntil()).isNull();
	}

	@Test
	void clearingFailuresUnlocks() {
		Long id = users.saveAndFlush(User.create("clear@example.com", "{noop}secret", "Clear", null, Set.of(Role.USER), null)).getId();
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		for (int attempt = 1; attempt <= 5; attempt++) {
			recordFailure("clear@example.com", now);
		}

		users.clearFailedLogins(id, now);

		User user = reload(id);
		assertThat(user.getFailedLoginCount()).isZero();
		assertThat(user.getFirstFailedLoginAt()).isNull();
		assertThat(user.isLocked(now)).isFalse();
	}

	@Test
	void failureForUnknownEmailChangesNothing() {
		assertThatCode(() -> recordFailure("nobody@example.com", Instant.now())).doesNotThrowAnyException();
	}

	private void recordFailure(String email, Instant now) {
		users.recordFailedLogin(email, now, now.minus(WINDOW), 5, now.plus(LOCK));
	}

	private User reload(Long id) {
		entityManager.clear(); // bulk updates bypass the persistence context
		return users.findById(id).orElseThrow();
	}

}
