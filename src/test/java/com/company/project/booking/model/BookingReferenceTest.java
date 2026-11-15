package com.company.project.booking.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class BookingReferenceTest {

	@Test
	void aReferenceIsHbAndEightCharactersWithoutLookAlikes() {
		Set<String> references = Stream.generate(BookingReference::next).limit(1000).collect(Collectors.toSet());

		assertThat(references).hasSize(1000).allMatch(reference -> reference.matches("HB[2-9A-HJKMNP-Z]{8}"));
		assertThat(BookingReference.ALPHABET).hasSize(31).doesNotContain("0", "O", "1", "I", "L");
	}

}
