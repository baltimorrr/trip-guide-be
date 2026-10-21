package com.company.project.common.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Fills {@code @CreatedDate} / {@code @LastModifiedDate} on entities (see BaseEntity).
 * Kept off the main class so {@code @DataJpaTest} slices opt in with {@code @Import(JpaAuditingConfig.class)}.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	/** PostgreSQL stores microseconds; truncate so a write response shows exactly what a later read returns. */
	@Bean
	DateTimeProvider auditingDateTimeProvider() {
		return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

}
