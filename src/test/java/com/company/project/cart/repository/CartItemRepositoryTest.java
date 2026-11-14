package com.company.project.cart.repository;

import static com.company.project.support.CatalogFixtures.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.company.project.cart.model.Cart;
import com.company.project.cart.model.CartItem;
import com.company.project.cart.model.RoomGuests;
import com.company.project.rate.repository.RatePlanRepository;
import com.company.project.support.CatalogFixtures;
import com.company.project.support.CatalogFixtures.CreatedHotel;
import com.company.project.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The V9 mappings and guards, and the hand-written cart queries. */
@IntegrationTest
class CartItemRepositoryTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(30);

	@Autowired
	JdbcClient jdbc;

	@Autowired
	CartRepository carts;

	@Autowired
	CartItemRepository cartItems;

	@Autowired
	RatePlanRepository ratePlans;

	@Autowired
	TransactionTemplate transactions;

	CatalogFixtures catalog;

	long userId;

	long ratePlanId;

	@BeforeEach
	void setUp() {
		catalog = new CatalogFixtures(jdbc);
		CreatedHotel hotel = catalog.hotel("Cart repo " + uniqueSuffix()).create();
		ratePlanId = catalog.defaultRatePlan(catalog.roomType(hotel.id(), 2, 2, 4, 5));
		userId = catalog.user();
	}

	@Test
	void readsALineBackWithSortedAgesAndTheHotelsCurrency() {
		long itemId = save(new RoomGuests(2, List.of(9, 4)), CHECK_IN);

		List<CartLineView> lines = cartItems.findLines(userId);

		assertThat(lines).hasSize(1);
		CartLineView line = lines.getFirst();
		assertThat(line.itemId()).isEqualTo(itemId);
		assertThat(line.ratePlanId()).isEqualTo(ratePlanId);
		assertThat(line.guests()).isEqualTo(new RoomGuests(2, List.of(4, 9)));
		assertThat(line.guests().childAges()).containsExactly(4, 9);
		assertThat(line.hotelCurrency()).isEqualTo("VND");
		assertThat(line.quantity()).isEqualTo(1);
		assertThat(line.priceAtAddMinor()).isEqualTo(2 * CatalogFixtures.DEFAULT_PRICE);
		assertThat(cartItems.findLines(catalog.user())).isEmpty();
	}

	@Test
	void oneLinePerRateStayAndGuests() {
		save(new RoomGuests(2, List.of(4, 9)), CHECK_IN);
		save(new RoomGuests(2, List.of()), CHECK_IN);

		assertThatThrownBy(() -> save(new RoomGuests(2, List.of(9, 4)), CHECK_IN))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletesOnlyLinesCheckingInBeforeTheDay() {
		// Days long past, so the delete can't touch another test's lines (the database is shared).
		LocalDate day = LocalDate.now().minusDays(10);
		catalog.cartItem(userId, ratePlanId, day.minusDays(1), day.plusDays(1), 1, 2);
		long onTheDay = catalog.cartItem(userId, ratePlanId, day, day.plusDays(1), 1, 2);

		assertThat(cartItems.deleteCheckedInBefore(day)).isPositive();

		assertThat(cartItems.findLines(userId)).extracting(CartLineView::itemId).containsExactly(onTheDay);
	}

	@Test
	void locksTheUsersCart() {
		save(new RoomGuests(1, List.of()), CHECK_IN);

		Long cartId = transactions.execute(status -> carts.findByUserIdForUpdate(userId).map(Cart::getId).orElseThrow());

		List<CartItem> inCart = transactions.execute(status -> cartItems.findInCart(cartId));
		Optional<Cart> none = transactions.execute(status -> carts.findByUserIdForUpdate(catalog.user()));
		assertThat(inCart).hasSize(1);
		assertThat(none).isEmpty();
	}

	private long save(RoomGuests guests, LocalDate checkIn) {
		return transactions.execute(status -> {
			Cart cart = carts.findByUserIdForUpdate(userId).orElseGet(() -> carts.saveAndFlush(Cart.create(userId)));
			CartItem item = CartItem.create(cart, ratePlans.getReferenceById(ratePlanId), checkIn, checkIn.plusDays(2),
					guests, 1, 2 * CatalogFixtures.DEFAULT_PRICE, "VND");
			return cartItems.saveAndFlush(item).getId();
		});
	}

}
