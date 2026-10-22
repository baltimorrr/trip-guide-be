package com.company.project.destination.sync;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Destination sync schedule under {@code app.destinations.sync}.
 *
 * @param cron when to run (Spring cron, six fields); {@code "-"} disables the schedule
 * @param onStartup run once as soon as the application is ready
 */
@Validated
@ConfigurationProperties("app.destinations.sync")
public record DestinationSyncProperties(@NotBlank String cron, boolean onStartup) {
}
