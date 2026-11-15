package com.company.project.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Which limits a request spends from. */
class RateLimitPolicyTest {

	@Test
	void theCartsPricedCallsShareTheSearchLimit() {
		assertThat(RateLimitPolicy.matching(request("GET", "/api/cart")))
			.containsExactly(RateLimitPolicy.GENERAL, RateLimitPolicy.SEARCH);
		assertThat(RateLimitPolicy.matching(request("POST", "/api/cart/items")))
			.containsExactly(RateLimitPolicy.GENERAL, RateLimitPolicy.SEARCH);
		assertThat(RateLimitPolicy.matching(request("PUT", "/api/cart/items/12")))
			.containsExactly(RateLimitPolicy.GENERAL, RateLimitPolicy.SEARCH);
		assertThat(RateLimitPolicy.matching(request("POST", "/api/cart/merge")))
			.containsExactly(RateLimitPolicy.GENERAL, RateLimitPolicy.SEARCH);
	}

	@Test
	void quotingAndBookingShareTheCheckoutLimit() {
		assertThat(RateLimitPolicy.matching(request("POST", "/api/bookings/quote")))
			.containsExactly(RateLimitPolicy.GENERAL, RateLimitPolicy.CHECKOUT);
		assertThat(RateLimitPolicy.matching(request("POST", "/api/bookings")))
			.containsExactly(RateLimitPolicy.GENERAL, RateLimitPolicy.CHECKOUT);
	}

	@Test
	void removingALinePricesNothing() {
		assertThat(RateLimitPolicy.matching(request("DELETE", "/api/cart/items/12")))
			.containsExactly(RateLimitPolicy.GENERAL);
	}

	@Test
	void theSearchEndpointsKeepTheirLimit() {
		assertThat(RateLimitPolicy.matching(request("GET", "/api/search/hotels"))).contains(RateLimitPolicy.SEARCH);
		assertThat(RateLimitPolicy.matching(request("GET", "/api/hotels/some-hotel/rooms")))
			.contains(RateLimitPolicy.SEARCH);
		assertThat(RateLimitPolicy.matching(request("GET", "/api/hotels/some-hotel")))
			.containsExactly(RateLimitPolicy.GENERAL);
	}

	private static MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setServletPath(path);
		return request;
	}

}
