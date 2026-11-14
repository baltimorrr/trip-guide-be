package com.company.project.common.ratelimit;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Which requests each limit covers. Every request spends from {@link #GENERAL}; a request matching one of the others
 * also spends from that one. Endpoints listed together share one bucket. A new sensitive or expensive endpoint gets a
 * matcher here.
 */
public enum RateLimitPolicy {

	GENERAL(AnyRequestMatcher.INSTANCE),

	LOGIN(post("/api/auth/login")),

	REGISTER(post("/api/auth/register")),

	/** Each of these may send an email, so one client can't mail thousands of addresses. */
	EMAIL(post("/api/auth/verify-email/resend", "/api/auth/password/forgot")),

	/** The priced availability queries, the most expensive SQL in the app; the cart runs them for every line's stay. */
	SEARCH(new OrRequestMatcher(get("/api/search/hotels", "/api/search/hotels/filters", "/api/hotels/*/rooms",
			"/api/cart"), post("/api/cart/items", "/api/cart/merge"), put("/api/cart/items/*")));

	private final RequestMatcher matcher;

	RateLimitPolicy(RequestMatcher matcher) {
		this.matcher = matcher;
	}

	/** The policies a request spends from, {@link #GENERAL} first. */
	static List<RateLimitPolicy> matching(HttpServletRequest request) {
		return Arrays.stream(values()).filter(policy -> policy.matcher.matches(request)).toList();
	}

	RateLimitProperties.Limit limit(RateLimitProperties.Limits limits) {
		return switch (this) {
			case GENERAL -> limits.general();
			case LOGIN -> limits.login();
			case REGISTER -> limits.register();
			case EMAIL -> limits.email();
			case SEARCH -> limits.search();
		};
	}

	private static RequestMatcher post(String... paths) {
		return matcher(HttpMethod.POST, paths);
	}

	private static RequestMatcher get(String... paths) {
		return matcher(HttpMethod.GET, paths);
	}

	private static RequestMatcher put(String... paths) {
		return matcher(HttpMethod.PUT, paths);
	}

	private static RequestMatcher matcher(HttpMethod method, String... paths) {
		return new OrRequestMatcher(Stream.of(paths)
			.map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(method, path))
			.toList());
	}

}
