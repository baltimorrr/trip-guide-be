package com.company.project.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import javax.crypto.SecretKey;

import com.company.project.auth.dto.TokenResponse;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import org.junit.jupiter.api.Test;
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

	final JwtDecoder decoder = config.jwtDecoder(key, properties);

	final TokenService tokenService = new TokenService(config.jwtEncoder(key), properties);

	@Test
	void issuedTokenCarriesUserClaims() {
		TokenResponse token = tokenService.issue(user(42L));

		Jwt jwt = decoder.decode(token.accessToken());
		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("spring-base");
		assertThat(jwt.getClaimAsString(TokenService.EMAIL_CLAIM)).isEqualTo("jane@example.com");
		assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly("ADMIN", "USER");
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(TTL);
		assertThat(token.expiresIn()).isEqualTo(TTL.toSeconds());
		assertThat(token.tokenType()).isEqualTo("Bearer");
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() {
		JwtProperties otherProperties = properties("a-completely-different-32-byte-secret!!");
		JwtEncoder otherEncoder = config.jwtEncoder(config.jwtSigningKey(otherProperties));
		String token = new TokenService(otherEncoder, otherProperties).issue(user(1L)).accessToken();

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
		User user = User.create("jane@example.com", "{noop}irrelevant", "Jane", Set.of(Role.USER, Role.ADMIN));
		// The id is normally assigned by the database.
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

}
