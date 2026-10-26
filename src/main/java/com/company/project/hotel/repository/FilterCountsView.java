package com.company.project.hotel.repository;

import java.util.Map;

/**
 * How many hotels each filter option would show, and the cheapest and dearest offer totals for the stay among the
 * results without the price filter, in the price currency.
 *
 * @param hotels hotels per option value, per group; an option missing here matches none
 * @param minTotalMinor null when no result sells in the price currency
 * @param maxTotalMinor null when no result sells in the price currency
 */
public record FilterCountsView(Map<FilterGroup, Map<String, Long>> hotels, Long minTotalMinor, Long maxTotalMinor) {

	/** Nothing matches. */
	public static final FilterCountsView EMPTY = new FilterCountsView(Map.of(), null, null);

	public long hotels(FilterGroup group, String value) {
		return hotels.getOrDefault(group, Map.of()).getOrDefault(value, 0L);
	}

}
