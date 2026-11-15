package com.company.project.booking.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Checkout settings under {@code app.booking}.
 *
 * @param hold how long a new Pending booking holds its rooms while the customer pays
 * @param expiryCron when to expire Pending bookings whose hold has passed (Spring cron, six fields); {@code "-"}
 * disables it
 */
@Validated
@ConfigurationProperties("app.booking")
public record BookingProperties(@NotNull Duration hold, @NotBlank String expiryCron) {
}
