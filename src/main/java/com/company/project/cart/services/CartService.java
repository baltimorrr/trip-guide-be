package com.company.project.cart.services;

import com.company.project.cart.dto.CartItemRequest;
import com.company.project.cart.dto.CartItemUpdateRequest;
import com.company.project.cart.dto.CartMergeRequest;
import com.company.project.cart.dto.CartMergeResponse;
import com.company.project.cart.dto.CartResponse;

/**
 * Each signed-in user's cart (C-CART-01..04): lines of any hotel and dates, grouped by hotel and date range, priced
 * again on every read by the hotel page's room list rules. Guests keep their cart in the browser until they sign in.
 */
public interface CartService {

	/** The cart with every line's status and current price; empty when the user has never added anything. */
	CartResponse cart(long userId);

	/**
	 * Adds the rooms to the cart, or to its line with the same rate plan, stay and guests, and returns the cart.
	 * Throws BusinessException 400 for a stay the search would reject or guests the room can't hold, 409 when the rate
	 * can't sell the stay, too few rooms are left for the group or the cart is full, and ResourceNotFoundException
	 * (404) for an unknown rate plan. Nothing changes when it throws.
	 */
	CartResponse add(long userId, CartItemRequest request);

	/**
	 * Gives the line new dates and a number of rooms, by the same checks as {@link #add}, and returns the cart. New
	 * dates that match another line of the same rate and guests merge the two. ResourceNotFoundException (404) when the
	 * line isn't in this user's cart.
	 */
	CartResponse update(long userId, long itemId, CartItemUpdateRequest request);

	/** Removes the line from this user's cart; nothing happens when it isn't there. */
	void remove(long userId, long itemId);

	/**
	 * Adds a guest's browser cart after sign-in. Each item is added like {@link #add}, except that a line already in
	 * the cart keeps the larger of the two quantities, so merging twice changes nothing. Items that can't be added
	 * are skipped and reported instead of failing the call. BusinessException 400 when there are more items than a
	 * cart holds.
	 */
	CartMergeResponse merge(long userId, CartMergeRequest request);

	/**
	 * Deletes every line whose check-in has passed everywhere ({@code SearchCriteria.today()}) (C-CART-04).
	 *
	 * @return how many lines were deleted
	 */
	int removeExpiredItems();

}
