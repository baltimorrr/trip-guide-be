package com.company.project.auth.security;

import java.time.Instant;

import com.company.project.auth.dto.TokenResponse;
import com.company.project.user.model.Role;
import com.company.project.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed access tokens. Verification is done by Spring Security's resource server. */
@Service
@RequiredArgsConstructor
public class TokenService {

	static final String ROLES_CLAIM = "roles";

	static final String EMAIL_CLAIM = "email";

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	public TokenResponse issue(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTokenTtl()))
				.claim(EMAIL_CLAIM, user.getEmail())
				.claim(ROLES_CLAIM, user.getRoles().stream().map(Role::name).sorted().toList())
				.build();
		// The header must name HS256 explicitly; without it the encoder looks for an RSA key.
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new TokenResponse(token, "Bearer", properties.accessTokenTtl().toSeconds());
	}

}
