package com.company.project.cart.repository.impl;

import java.time.LocalDate;
import java.util.List;

import com.company.project.cart.model.CartItem;
import com.company.project.cart.repository.CartItemRepositoryCustom;
import com.company.project.cart.repository.CartLineView;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hand-written cart line queries, all JPQL. Spring Data finds this class by its name ({@code CartItemRepository} +
 * "Impl") and merges it into the {@code CartItemRepository} bean.
 */
@RequiredArgsConstructor
public class CartItemRepositoryImpl implements CartItemRepositoryCustom {

	// No status filter on the rate plan, room type or hotel: the room list query decides what can still be sold.
	private static final String LINES = """
			select new com.company.project.cart.repository.CartLineView(
			       i.id, h.id, rt.id, rp.id, h.currency, i.checkIn, i.checkOut, i.quantity, i.adults, i.childAges,
			       i.priceAtAddMinor, i.currency)
			from CartItem i
			join i.cart c
			join i.ratePlan rp
			join rp.roomType rt
			join rt.hotel h
			where c.userId = :userId
			order by i.id""";

	private static final String IN_CART = """
			select i from CartItem i
			join fetch i.ratePlan rp
			join fetch rp.roomType
			where i.cart.id = :cartId
			order by i.id""";

	private final EntityManager entityManager;

	@Override
	public List<CartLineView> findLines(long userId) {
		return entityManager.createQuery(LINES, CartLineView.class).setParameter("userId", userId).getResultList();
	}

	@Override
	public List<CartItem> findInCart(long cartId) {
		return entityManager.createQuery(IN_CART, CartItem.class).setParameter("cartId", cartId).getResultList();
	}

	@Override
	@Transactional
	public int deleteCheckedInBefore(LocalDate day) {
		return entityManager.createQuery("delete from CartItem i where i.checkIn < :day")
			.setParameter("day", day)
			.executeUpdate();
	}

}
