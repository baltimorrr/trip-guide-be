package com.company.project.rate.repository;

/**
 * Who sells a rate plan: its room type and hotel, and the hotel's selling currency, which every sellable plan of the
 * hotel uses.
 */
public record RatePlanSellerView(Long ratePlanId, Long roomTypeId, Long hotelId, String hotelCurrency) {
}
