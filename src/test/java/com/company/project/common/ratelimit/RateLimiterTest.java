package com.company.project.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

import com.company.project.common.ratelimit.RateLimitProperties.Limit;
import com.company.project.common.ratelimit.RateLimitProperties.Limits;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

	/** 10 per minute: one request comes back every 6 seconds. */
	private static final Limit TEN_PER_MINUTE = new Limit(10, Duration.ofMinutes(1));

	private final ManualClock clock = new ManualClock();

	private final RateLimiter limiter = new RateLimiter(new RateLimitProperties(true, List.of(), 1000,
			new Limits(TEN_PER_MINUTE, TEN_PER_MINUTE, TEN_PER_MINUTE, TEN_PER_MINUTE, TEN_PER_MINUTE,
					TEN_PER_MINUTE)), clock);

	@Test
	void theCapacityPassesThenTheNextRequestWaitsForOneRefill() {
		spend(RateLimitPolicy.LOGIN, "203.0.113.1", 10);

		assertThat(limiter.secondsToWait(RateLimitPolicy.LOGIN, "203.0.113.1")).hasValue(6);
	}

	@Test
	void oneRequestComesBackPerRefillInterval() {
		spend(RateLimitPolicy.LOGIN, "203.0.113.1", 10);

		clock.advance(Duration.ofSeconds(6));

		assertThat(limiter.secondsToWait(RateLimitPolicy.LOGIN, "203.0.113.1")).isEmpty();
		assertThat(limiter.secondsToWait(RateLimitPolicy.LOGIN, "203.0.113.1")).isPresent();
	}

	@Test
	void theWaitRoundsUpAndIsNeverZero() {
		spend(RateLimitPolicy.LOGIN, "203.0.113.1", 10);

		clock.advance(Duration.ofMillis(5_001));
		assertThat(limiter.secondsToWait(RateLimitPolicy.LOGIN, "203.0.113.1")).hasValue(1);

		clock.advance(Duration.ofMillis(998));
		assertThat(limiter.secondsToWait(RateLimitPolicy.LOGIN, "203.0.113.1")).hasValue(1);
	}

	@Test
	void clientsAndPoliciesHaveTheirOwnBuckets() {
		spend(RateLimitPolicy.LOGIN, "203.0.113.1", 10);

		assertThat(limiter.secondsToWait(RateLimitPolicy.LOGIN, "203.0.113.2")).isEmpty();
		assertThat(limiter.secondsToWait(RateLimitPolicy.SEARCH, "203.0.113.1")).isEmpty();
		assertThat(limiter.secondsToWait(RateLimitPolicy.GENERAL, "203.0.113.1")).isEmpty();
	}

	@Test
	void anIpv4AddressIsItsOwnClient() {
		assertThat(RateLimiter.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
	}

	@Test
	void ipv6AddressesInOneSlash64AreOneClient() {
		String key = RateLimiter.clientKey("2001:db8:1:2:aaaa::1");

		assertThat(RateLimiter.clientKey("2001:db8:1:2:bbbb:cccc:dddd:9")).isEqualTo(key);
		assertThat(RateLimiter.clientKey("2001:db8:1:3::1")).isNotEqualTo(key);
		assertThat(key).isEqualTo("2001:db8:1:2:0:0:0:0/64");
	}

	@Test
	void anIpv4MappedAddressIsItsIpv4Address() {
		assertThat(RateLimiter.clientKey("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
	}

	@Test
	void textThatIsNoIpv6LiteralIsUsedAsGiven() {
		assertThat(RateLimiter.clientKey("not-an-address")).isEqualTo("not-an-address");
		assertThat(RateLimiter.clientKey("fe80::1%eth0")).isEqualTo("fe80::1%eth0");
		assertThat(RateLimiter.clientKey("1:2:3")).isEqualTo("1:2:3");
		assertThat(RateLimiter.clientKey(null)).isNull();
	}

	private void spend(RateLimitPolicy policy, String client, int requests) {
		for (int i = 0; i < requests; i++) {
			OptionalLong wait = limiter.secondsToWait(policy, client);
			assertThat(wait).as("request %d", i + 1).isEmpty();
		}
	}

	private static final class ManualClock implements TimeMeter {

		private long nanos;

		void advance(Duration duration) {
			nanos += duration.toNanos();
		}

		@Override
		public long currentTimeNanos() {
			return nanos;
		}

		@Override
		public boolean isWallClockBased() {
			return false;
		}

	}

}
