package com.company.project.cart.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cart limits and the expiry schedule under {@code app.cart}. Quantities and guests follow the search limits
 * ({@code app.search}).
 *
 * @param maxItems most lines in one cart; every read prices each line's stay, so this bounds its cost
 * @param expiryCron when to delete lines whose check-in has passed (Spring cron, six fields); {@code "-"} disables it
 */
@Validated
@ConfigurationProperties("app.cart")
public record CartProperties(@Positive int maxItems, @NotBlank String expiryCron) {
}
