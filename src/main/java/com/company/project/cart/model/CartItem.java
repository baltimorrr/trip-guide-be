package com.company.project.cart.model;

import java.time.LocalDate;

import com.company.project.common.model.BaseEntity;
import com.company.project.rate.model.RatePlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One line of a cart: {@code quantity} rooms of one rate plan for one stay, each holding the same guests. A line is
 * identified by its rate plan, dates and guests (one line per cart for each). The hotel is the rate plan's.
 * <p>
 * {@code priceAtAddMinor} is one room's price for the whole stay when the user last picked it, kept only to tell them
 * that the price changed: the cart is always priced again from the rates.
 */
@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cart_id", nullable = false, updatable = false)
	private Cart cart;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rate_plan_id", nullable = false, updatable = false)
	private RatePlan ratePlan;

	@Column(name = "check_in", nullable = false)
	private LocalDate checkIn;

	@Column(name = "check_out", nullable = false)
	private LocalDate checkOut;

	@Column(nullable = false)
	private short quantity;

	@Column(nullable = false, updatable = false)
	private short adults;

	@Column(nullable = false, updatable = false)
	private short children;

	@Column(name = "child_ages", nullable = false, updatable = false)
	private Short[] childAges;

	@Column(name = "price_at_add_minor", nullable = false)
	private long priceAtAddMinor;

	@Column(nullable = false, length = 3)
	private String currency;

	public static CartItem create(Cart cart, RatePlan ratePlan, LocalDate checkIn, LocalDate checkOut,
			RoomGuests guests, int quantity, long priceAtAddMinor, String currency) {
		CartItem item = new CartItem();
		item.cart = cart;
		item.ratePlan = ratePlan;
		item.checkIn = checkIn;
		item.checkOut = checkOut;
		item.adults = (short) guests.adults();
		item.children = (short) guests.children();
		item.childAges = guests.childAges().stream().map(Integer::shortValue).toArray(Short[]::new);
		item.quantity = (short) quantity;
		item.priceAtAddMinor = priceAtAddMinor;
		item.currency = currency;
		return item;
	}

	public RoomGuests guests() {
		return RoomGuests.of(adults, childAges);
	}

	/** Whether this line is the one for that rate plan, stay and guests. */
	public boolean isLine(long ratePlanId, LocalDate checkIn, LocalDate checkOut, RoomGuests guests) {
		return ratePlan.getId() == ratePlanId && this.checkIn.equals(checkIn) && this.checkOut.equals(checkOut)
				&& guests().equals(guests);
	}

	/** Same stay, another number of rooms: the price the user saw stays the notice's reference. */
	public void changeQuantity(int quantity) {
		this.quantity = (short) quantity;
	}

	/** Another stay: the user picks it at today's price, which becomes the notice's reference. */
	public void changeStay(LocalDate checkIn, LocalDate checkOut, long priceAtAddMinor, String currency) {
		this.checkIn = checkIn;
		this.checkOut = checkOut;
		rebasePrice(priceAtAddMinor, currency);
	}

	/** The user picked this line again at today's price. */
	public void rebasePrice(long priceAtAddMinor, String currency) {
		this.priceAtAddMinor = priceAtAddMinor;
		this.currency = currency;
	}

}
