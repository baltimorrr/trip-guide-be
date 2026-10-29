package com.company.project.common.ratelimit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.regex.Pattern;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

/**
 * One in-memory token bucket per limit and client. Memory is bounded: each limit keeps at most
 * {@code maxTrackedClients} buckets, and drops a bucket left idle for its period (it would be full again by then, so
 * nothing is lost). Counts are per instance and start over on restart.
 */
final class RateLimiter {

	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	/** Hex digits, colons and dots, starting with a hex digit or colon: the JDK parses such text as an IPv6 literal. */
	private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9a-fA-F:][0-9a-fA-F:.]*");

	private final Map<RateLimitPolicy, PolicyBuckets> policies = new EnumMap<>(RateLimitPolicy.class);

	private final TimeMeter time;

	RateLimiter(RateLimitProperties properties) {
		this(properties, TimeMeter.SYSTEM_NANOTIME);
	}

	/** Tests pass their own clock. */
	RateLimiter(RateLimitProperties properties, TimeMeter time) {
		this.time = time;
		for (RateLimitPolicy policy : RateLimitPolicy.values()) {
			RateLimitProperties.Limit limit = policy.limit(properties.limits());
			Cache<String, Bucket> buckets = Caffeine.newBuilder()
				.maximumSize(properties.maxTrackedClients())
				.expireAfterAccess(limit.period())
				.build();
			policies.put(policy, new PolicyBuckets(limit, buckets));
		}
	}

	/**
	 * Spends one request of {@code client} under {@code policy}.
	 *
	 * @return empty when the request may go ahead, else the whole seconds (at least 1) until one more is allowed
	 */
	OptionalLong secondsToWait(RateLimitPolicy policy, String client) {
		PolicyBuckets policyBuckets = policies.get(policy);
		ConsumptionProbe probe = policyBuckets.buckets()
			.get(client, key -> newBucket(policyBuckets.limit()))
			.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			return OptionalLong.empty();
		}
		return OptionalLong.of(Math.max(1, Math.ceilDiv(probe.getNanosToWaitForRefill(), NANOS_PER_SECOND)));
	}

	/**
	 * The client a request is counted against. An IPv4 address is used as given. An IPv6 address counts as its /64,
	 * because one host usually owns a whole /64, and an IPv4-mapped one as its IPv4 address. Anything else is used as
	 * given. Never a DNS lookup: text that isn't an IP literal is never passed to {@link InetAddress}.
	 */
	static String clientKey(String remoteAddress) {
		if (remoteAddress == null || remoteAddress.indexOf(':') < 0 || !IPV6_LITERAL.matcher(remoteAddress).matches()) {
			return remoteAddress;
		}
		try {
			InetAddress address = InetAddress.getByName(remoteAddress);
			if (address instanceof Inet6Address) {
				byte[] prefix = address.getAddress();
				Arrays.fill(prefix, 8, 16, (byte) 0);
				return InetAddress.getByAddress(prefix).getHostAddress() + "/64";
			}
			return address.getHostAddress();
		}
		catch (UnknownHostException ex) {
			return remoteAddress;
		}
	}

	private Bucket newBucket(RateLimitProperties.Limit limit) {
		return Bucket.builder()
			.addLimit(bandwidth -> bandwidth.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
			.withCustomTimePrecision(time)
			.build();
	}

	private record PolicyBuckets(RateLimitProperties.Limit limit, Cache<String, Bucket> buckets) {
	}

}
