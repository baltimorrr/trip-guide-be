package com.company.project.cart.services.impl;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.company.project.cart.dto.CartItemResponse;
import com.company.project.cart.dto.CartItemStatus;
import com.company.project.cart.services.CartService;
import com.company.project.search.model.SearchCriteria;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Lines whose check-in has passed are reported as such, then deleted by the expiry run (C-CART-04). */
@IntegrationTest
class CartExpiryTest {

	@Autowired
	JdbcClient jdbc;

	@Autowired
	CartService cartService;

	@Test
	void deletesOnlyLinesCheckingInBeforeToday() {
		CatalogFixtures catalog = new CatalogFixtures(jdbc);
		CreatedHotel hotel = catalog.hotel("Expiry " + uniqueSuffix()).create();
		long plan = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 0, 2, 5));
		long userId = catalog.user();
		LocalDate today = SearchCriteria.today();
		long passed = catalog.cartItem(userId, plan, today.minusDays(1), today.plusDays(1), 1, 2);
		long checkingInToday = catalog.cartItem(userId, plan, today, today.plusDays(1), 1, 2);

		assertThat(cartService.cart(userId).groups())
			.flatExtracting(group -> group.items())
			.filteredOn(item -> item.id() == passed)
			.extracting(CartItemResponse::status)
			.containsExactly(CartItemStatus.CHECK_IN_PASSED);

		assertThat(cartService.removeExpiredItems()).isPositive();

		assertThat(cartService.cart(userId).groups())
			.flatExtracting(group -> group.items())
			.extracting(CartItemResponse::id)
			.containsExactly(checkingInToday);
	}

}
