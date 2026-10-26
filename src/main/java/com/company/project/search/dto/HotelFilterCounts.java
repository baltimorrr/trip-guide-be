package com.company.project.search.dto;

import java.util.List;

/**
 * The options of every result filter with their hotel counts (C-SRCH-02). Each list is named after the query
 * parameter its values go into. A count is how many hotels the list would show if that option were picked: within
 * {@code stars}, {@code accommodationTypes}, {@code boardTypes}, {@code cancellation} and {@code bedTypes} (any of the
 * picked values) the list's own selection is ignored; {@code facilities} and {@code roomFeatures} (all picked values)
 * count the current results that also have the option; numeric options count each value on its own. Every option is
 * listed, even with no hotel.
 *
 * @param currency what prices are compared in; null when nothing is listed in the destination
 * @param priceRange null when no result sells in {@code currency}
 */
public record HotelFilterCounts(String currency, PriceRange priceRange, List<FilterOption> stars,
		List<FilterOption> minGoogleRating, List<FilterOption> minUserRating, List<FilterOption> accommodationTypes,
		List<FilterOption> facilities, List<FilterOption> maxDistanceM, List<FilterOption> boardTypes,
		List<FilterOption> cancellation, List<FilterOption> bedTypes, List<FilterOption> roomFeatures) {
}
