package com.company.project.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

/** Plain unit tests: the JWT beans are built directly, no Spring context or database. */
class TokenServiceTest {

	static final Duration TTL = Duration.ofMinutes(15);

	final JwtConfig config = new JwtConfig();

	final JwtProperties properties = properties("unit-test-secret-that-is-at-least-32-bytes");

	final SecretKey key = config.jwtSigningKey(properties);

	final AtomicInteger sessionChecks = new AtomicInteger();

	/** Stands in for the database session check. */
	final OAuth2TokenValidator<Jwt> liveSession = jwt -> {
		sessionChecks.incrementAndGet();
		return OAuth2TokenValidatorResult.success();
	};

	final JwtDecoder decoder = config.jwtDecoder(key, properties, liveSession);

	final TokenService tokenService = new TokenService(config.jwtEncoder(key), properties);

	@Test
	void issuedTokenCarriesUserClaims() {
		String token = tokenService.issue(user(42L), 7L);

		Jwt jwt = decoder.decode(token);
		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getClaimAsString(TokenService.SESSION_CLAIM)).isEqualTo("7");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("spring-base");
		assertThat(jwt.getClaimAsString(TokenService.EMAIL_CLAIM)).isEqualTo("jane@example.com");
		assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly("ADMIN", "USER");
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(TTL);
		assertThat(sessionChecks).hasValue(1);
	}

	@Test
	void tokenOfAnInactiveSessionIsRejected() {
		JwtDecoder revoked = config.jwtDecoder(key, properties,
				jwt -> OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token")));

		assertThatThrownBy(() -> revoked.decode(tokenService.issue(user(42L), 7L)))
				.isInstanceOf(JwtValidationException.class);
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() {
		JwtProperties otherProperties = properties("a-completely-different-32-byte-secret!!");
		JwtEncoder otherEncoder = config.jwtEncoder(config.jwtSigningKey(otherProperties));
		String token = new TokenService(otherEncoder, otherProperties).issue(user(1L), 1L);

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void expiredTokenIsRejected() {
		Instant past = Instant.now().minus(Duration.ofMinutes(20));
		String token = encode(JwtClaimsSet.builder()
				.issuer("spring-base").subject("1")
				.issuedAt(past).expiresAt(past.plus(Duration.ofMinutes(15)))
				.build());

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
		// Rejected before the session check, so a stale token never costs a database lookup.
		assertThat(sessionChecks).hasValue(0);
	}

	@Test
	void tokenFromAnotherIssuerIsRejected() {
		Instant now = Instant.now();
		String token = encode(JwtClaimsSet.builder()
				.issuer("someone-else").subject("1")
				.issuedAt(now).expiresAt(now.plus(TTL))
				.build());

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
	}

	@Test
	void secretShorterThan32BytesIsRejected() {
		assertThatThrownBy(() -> config.jwtSigningKey(properties("only-16-bytes!!!")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("at least 32 bytes");
	}

	@Test
	void secretThatIsNotBase64IsRejected() {
		JwtProperties notBase64 = new JwtProperties("not base64 !!", "spring-base", TTL);

		assertThatThrownBy(() -> config.jwtSigningKey(notBase64))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must be base64");
	}

	private String encode(JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return config.jwtEncoder(key).encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static JwtProperties properties(String rawSecret) {
		String secret = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
		return new JwtProperties(secret, "spring-base", TTL);
	}

	private static User user(long id) {
		User user = User.create("jane@example.com", "{noop}irrelevant", "Jane", null, Set.of(Role.USER, Role.ADMIN), null);
		// The id is normally assigned by the database.
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
