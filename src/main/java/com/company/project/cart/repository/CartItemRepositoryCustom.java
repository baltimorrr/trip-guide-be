package com.company.project.cart.repository;

import java.time.LocalDate;
import java.util.List;

import com.company.project.cart.model.CartItem;

/** Hand-written cart line queries, implemented in {@code impl/CartItemRepositoryImpl}. */
public interface CartItemRepositoryCustom {

	/**
	 * The user's lines in the order they were added, with each one's hotel and room type. Lines whose rate plan, room
	 * type or hotel stopped selling are included: the cart marks them unavailable.
	 */
	List<CartLineView> findLines(long userId);

	/** The cart's lines in the order they were added, with their rate plan and room type loaded. */
	List<CartItem> findInCart(long cartId);

	/** Deletes every line, of any cart, whose check-in is before {@code day}; returns how many. */
	int deleteCheckedInBefore(LocalDate day);

}
