package com.company.project.wishlist.repository;

import java.time.Instant;

/**
 * One wishlist entry, read with its hotel's listing state.
 *
 * @param available whether the hotel is still listed (ACTIVE and not deleted)
 */
public record WishlistEntryView(Long hotelId, Instant savedAt, boolean available) {
}
