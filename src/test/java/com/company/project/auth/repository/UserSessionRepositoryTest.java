package com.company.project.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import com.company.project.TestcontainersConfiguration;
import com.company.project.auth.model.UserSession;
import com.company.project.auth.security.OpaqueTokens;
import com.company.project.common.config.JpaAuditingConfig;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import com.company.project.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** JPA slice (same configuration as UserRepositoryTest, so the context is shared); each test is rolled back. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JpaAuditingConfig.class })
@ActiveProfiles("test")
class UserSessionRepositoryTest {

	@Autowired
	UserSessionRepository sessions;

	@Autowired
	UserRepository users;

	@Autowired
	EntityManager entityManager;

	@Test
	void revokeAllTouchesOnlyThatUsersLiveSessions() {
		User jane = user("jane-sessions@example.com");
		User other = user("other-sessions@example.com");
		// PostgreSQL keeps microseconds.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		UserSession phone = session(jane, now);
		UserSession laptop = session(jane, now);
		UserSession alreadyRevoked = session(jane, now);
		alreadyRevoked.revoke(now.minusSeconds(60));
		UserSession othersPhone = session(other, now);
		entityManager.flush();

		int revoked = sessions.revokeAll(jane.getId(), now);

		assertThat(revoked).isEqualTo(2);
		entityManager.clear();
		assertThat(sessions.findById(phone.getId()).orElseThrow().getRevokedAt()).isEqualTo(now);
		assertThat(sessions.findById(laptop.getId()).orElseThrow().getVersion()).isEqualTo(1);
		assertThat(sessions.findById(alreadyRevoked.getId()).orElseThrow().getRevokedAt()).isBefore(now);
		assertThat(sessions.findById(othersPhone.getId()).orElseThrow().getRevokedAt()).isNull();
	}

	@Test
	void revokeAllExceptKeepsTheGivenSession() {
		User jane = user("jane-keep@example.com");
		Instant now = Instant.now();
		UserSession current = session(jane, now);
		UserSession other = session(jane, now);
		entityManager.flush();

		assertThat(sessions.revokeAllExcept(jane.getId(), current.getId(), now)).isEqualTo(1);

		entityManager.clear();
		assertThat(sessions.findById(current.getId()).orElseThrow().getRevokedAt()).isNull();
		assertThat(sessions.findById(other.getId()).orElseThrow().getRevokedAt()).isNotNull();
	}

	@Test
	void sessionIsFoundByItsCurrentAndPreviousRefreshToken() {
		UserSession session = session(user("jane-rotate@example.com"), Instant.now());
		String first = session.getRefreshTokenHash();
		session.rotate(OpaqueTokens.hash(OpaqueTokens.generate()), Instant.now());
		entityManager.flush();

		assertThat(sessions.findByTokenHashForUpdate(session.getRefreshTokenHash())).contains(session);
		assertThat(sessions.findByTokenHashForUpdate(first)).contains(session);
		assertThat(sessions.findByTokenHashForUpdate(OpaqueTokens.hash("unknown"))).isEmpty();
		assertThat(sessions.findActiveByTokenHash(first, Instant.now())).isEmpty();
		assertThat(sessions.findActiveByTokenHash(session.getRefreshTokenHash(), Instant.now())).contains(session);
	}

	@Test
	void sessionCheckRejectsRevokedExpiredAndDisabled() {
		Instant now = Instant.now();
		UserSession live = session(user("live@example.com"), now);
		UserSession revoked = session(user("revoked@example.com"), now);
		revoked.revoke(now);
		UserSession expired = sessions.saveAndFlush(UserSession.create(user("expired@example.com"), hash(),
				now.minusSeconds(1), null, null));
		User disabled = user("disabled@example.com");
		UserSession ofDisabled = session(disabled, now);
		entityManager.flush();
		entityManager.createNativeQuery("update users set enabled = false where id = :id")
			.setParameter("id", disabled.getId())
			.executeUpdate();

		assertThat(sessions.existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue(live.getId(), now)).isTrue();
		assertThat(sessions.existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue(revoked.getId(), now)).isFalse();
		assertThat(sessions.existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue(expired.getId(), now)).isFalse();
		assertThat(sessions.existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue(ofDisabled.getId(), now)).isFalse();
	}

	private User user(String email) {
		return users.saveAndFlush(User.create(email, "{noop}secret", "Test", null, Set.of(Role.USER), Instant.now()));
	}

	private UserSession session(User user, Instant now) {
		return sessions.saveAndFlush(UserSession.create(user, hash(), now.plus(Duration.ofDays(1)), "JUnit", "127.0.0.1"));
	}

	private static String hash() {
		return OpaqueTokens.hash(OpaqueTokens.generate());
	}

}
