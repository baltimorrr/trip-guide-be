package com.company.project.booking.model;

import java.util.List;

import com.company.project.cart.repository.CartLineView;
import com.company.project.hotel.repository.RoomRateView;
import com.company.project.rate.repository.CancellationRuleView;
import com.company.project.rate.repository.RateOfferView;

/** A cart line priced for checkout: the room list's row for its rate and stay, and how the rate is described. */
public record PricedLine(CartLineView line, RoomRateView rate, RateOfferView offer, List<CancellationRuleView> rules) {

	/** Every room of the line for the whole stay. */
	public long subtotalMinor() {
		return rate.stayPriceMinor() * line.quantity();
	}

}
