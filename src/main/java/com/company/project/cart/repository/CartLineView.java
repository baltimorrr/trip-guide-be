package com.company.project.cart.repository;

import java.time.LocalDate;

import com.company.project.cart.model.RoomGuests;

/**
 * A cart line as the priced cart reads it.
 *
 * @param hotelCurrency the hotel's selling currency now, which every sellable rate plan of the hotel uses
 * @param priceAtAddMinor one room for the whole stay when the user last picked it, in {@code currency}
 */
public record CartLineView(Long itemId, Long hotelId, Long roomTypeId, Long ratePlanId, String hotelCurrency,
		LocalDate checkIn, LocalDate checkOut, int quantity, RoomGuests guests, long priceAtAddMinor, String currency) {

	/** The JPQL {@code select new} form: the {@code smallint} columns and the child ages as they are mapped. */
	public CartLineView(Long itemId, Long hotelId, Long roomTypeId, Long ratePlanId, String hotelCurrency,
			LocalDate checkIn, LocalDate checkOut, short quantity, short adults, Short[] childAges,
			long priceAtAddMinor, String currency) {
		this(itemId, hotelId, roomTypeId, ratePlanId, hotelCurrency, checkIn, checkOut, quantity,
				RoomGuests.of(adults, childAges), priceAtAddMinor, currency);
	}

}
