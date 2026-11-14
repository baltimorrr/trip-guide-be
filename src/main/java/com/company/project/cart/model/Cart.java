package com.company.project.cart.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A signed-in user's cart: one per user, created by the first room added. Guests keep their cart in the browser and
 * merge it in after signing in. Its lines are {@link CartItem}s; every write to them locks this row first.
 */
@Entity
@Table(name = "carts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	public static Cart create(long userId) {
		Cart cart = new Cart();
		cart.userId = userId;
		return cart;
	}

}
