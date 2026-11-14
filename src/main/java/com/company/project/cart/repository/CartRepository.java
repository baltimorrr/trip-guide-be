package com.company.project.cart.repository;

import com.company.project.cart.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

/** One cart per user. Hand-written queries are in {@link CartRepositoryCustom}. */
public interface CartRepository extends JpaRepository<Cart, Long>, CartRepositoryCustom {

}
