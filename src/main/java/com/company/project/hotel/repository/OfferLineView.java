package com.company.project.hotel.repository;

/**
 * One room line of a hotel's cheapest offer: {@code quantity} rooms of a room type sold on one rate plan, each
 * costing {@code stayPriceMinor} for the whole stay.
 */
public record OfferLineView(Long hotelId, Long roomTypeId, Long ratePlanId, int quantity, long stayPriceMinor,
		String currency) {

	public long totalMinor() {
		return quantity * stayPriceMinor;
	}

}
