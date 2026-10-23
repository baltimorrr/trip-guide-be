package com.company.project.auth.security;

import java.time.Instant;

import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed access tokens. Verification is done by Spring Security's resource server, which also checks that
 * the token's session ({@value #SESSION_CLAIM}) is still live.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

	static final String ROLES_CLAIM = "roles";

	static final String EMAIL_CLAIM = "email";

	/** The id of the user_sessions row the token belongs to, as a string. */
	public static final String SESSION_CLAIM = "sid";

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	/** A signed access token for the user, valid for {@code app.jwt.access-token-ttl}. */
	public String issue(User user, Long sessionId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTokenTtl()))
				.claim(EMAIL_CLAIM, user.getEmail())
				.claim(ROLES_CLAIM, user.getRoles().stream().map(Role::name).sorted().toList())
				.claim(SESSION_CLAIM, String.valueOf(sessionId))
				.build();
		// The header must name HS256 explicitly; without it the encoder looks for an RSA key.
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
