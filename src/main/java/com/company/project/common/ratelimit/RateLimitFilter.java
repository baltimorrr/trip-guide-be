package com.company.project.common.ratelimit;

import java.io.IOException;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import com.company.project.common.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spends one request from every limit that covers it ({@link RateLimitPolicy}) and answers 429 when one is used up,
 * before authentication and before the body is read.
 * <p>
 * SecurityConfig builds it and adds it to the security chain. It is not a bean: Boot would also register a
 * {@code Filter} bean in the servlet container, in front of Spring Security, where a 429 would miss the CORS headers.
 * Like ProblemDetailSecurityHandler, it hands the error to MVC's exception resolver so GlobalExceptionHandler renders
 * it.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

	/** Load balancer probes. A 429 would take a healthy instance out of rotation. Also matches /actuator/health. */
	private static final RequestMatcher HEALTH = PathPatternRequestMatcher.withDefaults().matcher("/actuator/health/**");

	private final RateLimiter limiter;

	private final Set<String> exemptClients;

	private final HandlerExceptionResolver exceptionResolver;

	public RateLimitFilter(RateLimitProperties properties, HandlerExceptionResolver exceptionResolver) {
		this.limiter = new RateLimiter(properties);
		this.exemptClients = properties.exemptAddresses().stream()
			.map(String::trim)
			.filter(address -> !address.isEmpty())
			.map(RateLimiter::clientKey)
			.collect(Collectors.toUnmodifiableSet());
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return HEALTH.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String client = RateLimiter.clientKey(request.getRemoteAddr());
		if (!exemptClients.contains(client)) {
			for (RateLimitPolicy policy : RateLimitPolicy.matching(request)) {
				OptionalLong wait = limiter.secondsToWait(policy, client);
				if (wait.isPresent()) {
					// DEBUG only: during an attack this would flood an INFO log.
					log.debug("Rate limit {} used up by {}", policy, client);
					exceptionResolver.resolveException(request, response, null,
							new RateLimitExceededException(wait.getAsLong()));
					return;
				}
			}
		}
		chain.doFilter(request, response);
	}

}
