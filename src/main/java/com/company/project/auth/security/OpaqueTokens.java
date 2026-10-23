package com.company.project.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random tokens handed to clients (refresh tokens, emailed links) and the hash stored for them. The tokens carry
 * 256 random bits, so a plain SHA-256 is enough: there is nothing to brute-force, unlike a password.
 */
public final class OpaqueTokens {

	private static final int TOKEN_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private OpaqueTokens() {
	}

	/** 43 URL-safe characters (base64url without padding), usable as-is in a link's query string. */
	public static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Lowercase hex SHA-256, 64 characters: what the database stores and looks tokens up by. */
	public static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			// Every Java platform must provide SHA-256.
			throw new IllegalStateException(ex);
		}
	}

}
