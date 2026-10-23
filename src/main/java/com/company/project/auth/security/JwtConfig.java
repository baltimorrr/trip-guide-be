package com.company.project.auth.security;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Signs and verifies access tokens with one shared HMAC key (HS256). Fine while this app is
 * both issuer and verifier; switch to an RSA key pair if other services must verify tokens.
 */
@Configuration
public class JwtConfig {

	/** HS256 needs a key of at least 256 bits. */
	static final int MIN_SECRET_BYTES = 32;

	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(properties.secret());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("app.jwt.secret must be base64. Generate one: openssl rand -base64 32");
		}
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("app.jwt.secret must decode to at least %d bytes, got %d. Generate one: openssl rand -base64 32"
					.formatted(MIN_SECRET_BYTES, bytes.length));
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
	}

	/** @param sessionTokenValidator checks the token's session is still live ({@link SessionTokenValidator}) */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties, OAuth2TokenValidator<Jwt> sessionTokenValidator) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		// Default validators check exp/nbf (60s clock skew); also require our issuer.
		OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(properties.issuer());
		// Chained by hand: DelegatingOAuth2TokenValidator runs every validator, and an expired or foreign token
		// should not cost a database lookup.
		decoder.setJwtValidator(jwt -> {
			var result = standard.validate(jwt);
			return result.hasErrors() ? result : sessionTokenValidator.validate(jwt);
		});
		return decoder;
	}

	/** Maps the {@code roles} claim (e.g. ["USER","ADMIN"]) to ROLE_USER / ROLE_ADMIN authorities. */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

}
