package com.company.project.hotel.repository;

/**
 * A filter option from reference data (board type, accommodation type, amenity, bed type).
 *
 * @param value the code the query parameter takes
 * @param category the heading an amenity is listed under; null for the others
 */
public record FilterOptionView(String value, String name, String category) {

	public FilterOptionView(String value, String name) {
		this(value, name, null);
	}

}
