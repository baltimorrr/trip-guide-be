package com.company.project.hotel.repository;

import java.util.List;

/**
 * The result filters that offer a list of options, each counted separately by the filter counts. Hotel filters
 * describe the hotel; offer filters describe the rate plan or room type an offer is made of.
 */
public enum FilterGroup {

	STARS(false, "1", "2", "3", "4", "5"),
	GOOGLE_RATING(false, "3.0", "3.5", "4.0", "4.5"),
	USER_RATING(false, "6", "7", "8", "9"),
	ACCOMMODATION_TYPE(false),
	FACILITIES(false),
	DISTANCE(false, "1000", "3000", "5000", "10000"),
	BOARD_TYPE(true),
	CANCELLATION(true),
	BED_TYPE(true),
	ROOM_FEATURES(true);

	private final boolean offerLevel;

	private final List<String> optionValues;

	FilterGroup(boolean offerLevel, String... optionValues) {
		this.offerLevel = offerLevel;
		this.optionValues = List.of(optionValues);
	}

	/** True when the filter limits which rate plans and room types an offer may use, false when it drops hotels. */
	public boolean offerLevel() {
		return offerLevel;
	}

	/**
	 * The fixed options of a numeric filter (stars, lowest ratings, farthest distance in metres), smallest first, as
	 * the query parameter takes them. Empty for filters whose options are reference data or an enum.
	 */
	public List<String> optionValues() {
		return optionValues;
	}

}
