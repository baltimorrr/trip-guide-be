package com.company.project.booking.model;

import java.security.SecureRandom;

/**
 * Booking references customers read out to a hotel: "HB" and 8 characters without the look-alikes 0/O and 1/I/L,
 * about 8.5 × 10^11 of them. A clash with an existing one is possible but rare, so callers check and draw again.
 */
public final class BookingReference {

	static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

	private static final int LENGTH = 8;

	private static final SecureRandom RANDOM = new SecureRandom();

	private BookingReference() {
	}

	public static String next() {
		StringBuilder reference = new StringBuilder("HB");
		for (int i = 0; i < LENGTH; i++) {
			reference.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return reference.toString();
	}

}
