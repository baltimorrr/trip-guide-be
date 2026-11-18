package com.company.project.common.config;

import java.util.List;
import java.util.stream.Stream;

import com.company.project.common.exception.ProblemDetailSecurityHandler;
import com.company.project.common.ratelimit.RateLimitFilter;
import com.company.project.common.ratelimit.RateLimitProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless API security: every request carries {@code Authorization: Bearer <jwt>}, except
 * the public endpoints below. Role checks live on controller methods ({@code @PreAuthorize}).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	/** Account endpoints anyone may call (all POST). They also ignore any Authorization header, see below. */
	private static final RequestMatcher PUBLIC_AUTH_ENDPOINTS = new OrRequestMatcher(Stream.of("/api/auth/register",
			"/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/verify-email",
			"/api/auth/verify-email/resend", "/api/auth/password/forgot", "/api/auth/password/reset")
		.map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path))
		.toList());

	/**
	 * Stripe's webhook: no token, as Stripe signs each call instead (checked by the payment feature). It ignores any
	 * Authorization header too, so a stray one can't turn Stripe's call into a 401.
	 */
	private static final RequestMatcher STRIPE_WEBHOOK = PathPatternRequestMatcher.withDefaults()
		.matcher(HttpMethod.POST, "/api/payments/stripe/webhook");

	private static final RequestMatcher NO_BEARER_TOKEN = new OrRequestMatcher(PUBLIC_AUTH_ENDPOINTS, STRIPE_WEBHOOK);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
			ProblemDetailSecurityHandler problemHandler, RateLimitProperties rateLimits,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		DefaultBearerTokenResolver bearerTokens = new DefaultBearerTokenResolver();
		http
				// No cookies or sessions: the token travels in a header, so CSRF cannot apply.
				// Re-enable CSRF if cookie-based auth is ever added.
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
						.requestMatchers(STRIPE_WEBHOOK).permitAll()
						// Homepage and hotel pages: guests search and browse without an account. Keep authenticated
						// hotel endpoints (owners, admins) out of /api/hotels.
						.requestMatchers(HttpMethod.GET, "/api/search/**", "/api/destinations/**", "/api/hotels/**")
						.permitAll()
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						// API docs; the prod profile switches springdoc off entirely.
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				// Validates bearer tokens. This entry point handles invalid/expired tokens...
				.oauth2ResourceServer(oauth2 -> oauth2
						// A client whose access token expired must still be able to refresh, sign in or sign out,
						// so the public account endpoints never read the header.
						.bearerTokenResolver(request -> NO_BEARER_TOKEN.matches(request) ? null : bearerTokens.resolve(request))
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(problemHandler))
				// ...and this one handles requests with no token at all, plus 403s.
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(problemHandler)
						.accessDeniedHandler(problemHandler));
		// After CORS, so a 429 carries the CORS headers and valid preflights (answered by CorsFilter) aren't counted.
		// Before authentication, so a flood of bad tokens is cut off before JWT checks and the session lookup.
		if (rateLimits.enabled()) {
			http.addFilterAfter(new RateLimitFilter(rateLimits, exceptionResolver), CorsFilter.class);
		}
		return http.build();
	}

	/** BCrypt by default; the {id} prefix in stored hashes allows upgrading the algorithm later. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * Used by the login endpoint. DaoAuthenticationProvider also hashes a dummy password for
	 * unknown emails, so response timing does not reveal which emails are registered.
	 */
	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	/** Browser origins allowed to call /api/**; comma-separated APP_CORS_ALLOWED_ORIGINS, empty = none. */
	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

}
