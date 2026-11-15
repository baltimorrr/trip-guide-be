package com.company.project.common.ratelimit;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rate limit settings under {@code app.rate-limit}. Every request is limited per client address, see
 * {@link RateLimitPolicy} for which requests each limit covers.
 *
 * @param enabled false leaves every request unlimited, e.g. when a gateway limits instead
 * @param exemptAddresses client addresses that are never limited
 * @param maxTrackedClients most clients remembered per limit; past that the least recently seen are forgotten
 * @param limits one limit per {@link RateLimitPolicy}
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, @NotNull List<String> exemptAddresses, @Min(1) int maxTrackedClients,
		@Valid @NotNull Limits limits) {

	public record Limits(@Valid @NotNull Limit general, @Valid @NotNull Limit login, @Valid @NotNull Limit register,
			@Valid @NotNull Limit email, @Valid @NotNull Limit search, @Valid @NotNull Limit checkout) {
	}

	/**
	 * @param capacity requests allowed at once
	 * @param period time to refill the whole capacity; tokens come back gradually, not all at the end
	 */
	public record Limit(@Min(1) long capacity, @NotNull Duration period) {

		public Limit {
			if (period != null && (period.isNegative() || period.isZero())) {
				throw new IllegalArgumentException("period must be positive");
			}
		}

	}

}
