package com.company.project.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpaqueTokensTest {

	@Test
	void tokensAreUrlSafeAndUnique() {
		String first = OpaqueTokens.generate();
		String second = OpaqueTokens.generate();

		assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(second).isNotEqualTo(first);
	}

	@Test
	void hashIsStableHexAndNeverTheToken() {
		String token = OpaqueTokens.generate();

		assertThat(OpaqueTokens.hash(token)).hasSize(64).matches("[0-9a-f]+").isEqualTo(OpaqueTokens.hash(token))
			.isNotEqualTo(token);
		// Known SHA-256 vector.
		assertThat(OpaqueTokens.hash("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

}
