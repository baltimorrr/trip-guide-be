package com.company.project.hotel.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Result filters of a hotel search (C-SRCH-02). Empty sets and nulls mean "any"; codes are matched as given, so an
 * unknown code matches nothing. Hotel filters drop hotels; offer filters ({@link FilterGroup#offerLevel()}) limit the
 * rate plans and room types an offer may use, and the price range checks the cheapest offer left.
 *
 * @param nameQuery part of the hotel name, already LIKE-escaped with backslashes; null = any
 * @param facilities hotel facilities that must all be present
 * @param boardTypes board type codes, any of them
 * @param cancellation {@code FREE} (free cancellation still possible at {@code now}) and/or {@code NON_REFUNDABLE}
 * @param bedTypes bed type codes, any of them in the room type
 * @param roomFeatures room features the room type must all have
 * @param minNightMinor lowest price per night of the whole offer, in minor units of {@code priceCurrency}
 * @param maxNightMinor highest price per night of the whole offer, in minor units of {@code priceCurrency}
 * @param priceCurrency the currency prices are compared in: only hotels selling in it match a price range, and the
 * price sort lists them first
 * @param now the moment free cancellation is judged at; the result cards use the same one
 */
public record HotelFilter(String nameQuery, Set<Integer> stars, BigDecimal minGoogleRating, BigDecimal minUserRating,
		Set<String> accommodationTypes, Set<String> facilities, Integer maxDistanceM, Set<String> boardTypes,
		Set<String> cancellation, Set<String> bedTypes, Set<String> roomFeatures, Long minNightMinor,
		Long maxNightMinor, String priceCurrency, Instant now) {
}
