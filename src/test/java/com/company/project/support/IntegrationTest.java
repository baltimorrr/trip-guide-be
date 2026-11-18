package com.company.project.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.company.project.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full-application test against a real PostgreSQL container.
 * <p>
 * Every integration test uses this one annotation so Spring caches a single application
 * context (and therefore a single container) for the whole test run. Emails are recorded by
 * {@link RecordingAuthMailService} and {@link RecordingBookingMailService} instead of being logged, and Stripe is
 * replaced by {@link FakePaymentGateway}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, RecordingAuthMailService.Config.class,
		RecordingBookingMailService.Config.class, FakePaymentGateway.Config.class })
@ActiveProfiles("test")
public @interface IntegrationTest {
}
