package com.company.project.hotel.repository;

import java.util.List;

/**
 * The cheapest way a hotel can host the searched stay: its room lines, cheapest first. One line covers all rooms
 * unless the cheapest room type has fewer free than were asked for. Every line is in the hotel's currency.
 */
public record HotelOfferView(Long hotelId, List<OfferLineView> lines) {

	public String currency() {
		return lines.getFirst().currency();
	}

	public long totalMinor() {
		return lines.stream().mapToLong(OfferLineView::totalMinor).sum();
	}

}
