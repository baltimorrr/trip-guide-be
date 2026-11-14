package com.company.project.cart.repository;

import java.util.Optional;

import com.company.project.cart.model.Cart;

/** Hand-written cart queries, implemented in {@code impl/CartRepositoryImpl}. */
public interface CartRepositoryCustom {

	/**
	 * The user's cart, row-locked until the transaction ends, so writes to one cart run one after the other and each
	 * sees the lines the previous one left.
	 */
	Optional<Cart> findByUserIdForUpdate(long userId);

}
