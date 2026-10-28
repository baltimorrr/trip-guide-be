package com.company.project.wishlist.services;

import com.company.project.wishlist.dto.WishlistHotel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Each signed-in user's saved hotels (C-SRCH-08): one list per user, saving and removing are idempotent. */
public interface WishlistService {

	/**
	 * The user's saved hotels, newest first, including those no longer listed ({@code available} false). Ignores any
	 * sort in the Pageable.
	 */
	Page<WishlistHotel> list(long userId, Pageable pageable);

	/**
	 * Saves a listed hotel; saving it again changes nothing. Throws ResourceNotFoundException (404) when the hotel
	 * doesn't exist or isn't listed.
	 */
	void save(long userId, long hotelId);

	/** Removes the hotel from the wishlist, listed or not; nothing happens when it wasn't saved. */
	void remove(long userId, long hotelId);

}
