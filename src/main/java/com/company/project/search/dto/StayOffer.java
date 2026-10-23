package com.company.project.search.dto;

import java.util.List;

import com.company.project.common.dto.Money;

/**
 * The cheapest way the hotel can host the searched stay.
 *
 * @param totalPrice every room for every night, before any tax added on top and before discounts
 * @param taxesIncluded true when the hotel's country adds no tax on top of room prices for these nights; the
 * amounts come with the checkout price breakdown
 * @param roomLines cheapest first; one line unless the cheapest room type has fewer rooms free than were asked for
 */
public record StayOffer(int nights, int rooms, Money totalPrice, boolean taxesIncluded, List<OfferRoom> roomLines) {
}
