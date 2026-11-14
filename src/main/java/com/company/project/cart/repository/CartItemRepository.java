package com.company.project.cart.repository;

import com.company.project.cart.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

/** Cart lines. Hand-written queries are in {@link CartItemRepositoryCustom}. */
public interface CartItemRepository extends JpaRepository<CartItem, Long>, CartItemRepositoryCustom {

	/** Removes the line if it is in this user's cart; returns how many were removed (0 or 1). */
	long deleteByIdAndCartUserId(Long id, Long userId);

}
