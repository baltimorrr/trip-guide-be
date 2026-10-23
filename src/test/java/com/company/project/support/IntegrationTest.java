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
 * context (and therefore a single container) for the whole test run. Emailed links are recorded by
 * {@link RecordingAuthMailer} instead of being logged.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, RecordingAuthMailer.Config.class })
@ActiveProfiles("test")
public @interface IntegrationTest {
}
