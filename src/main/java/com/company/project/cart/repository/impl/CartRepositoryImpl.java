package com.company.project.cart.repository.impl;

import java.util.Optional;

import com.company.project.cart.model.Cart;
import com.company.project.cart.repository.CartRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * Hand-written cart queries, all JPQL. Spring Data finds this class by its name ({@code CartRepository} + "Impl") and
 * merges it into the {@code CartRepository} bean.
 */
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepositoryCustom {

	private final EntityManager entityManager;

	@Override
	public Optional<Cart> findByUserIdForUpdate(long userId) {
		return entityManager.createQuery("select c from Cart c where c.userId = :userId", Cart.class)
			.setParameter("userId", userId)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.getResultStream()
			.findFirst();
	}

}
