package com.company.project.search.dto;

/**
 * One option of a result filter and how many hotels the list would show if it were picked.
 *
 * @param value what the filter's query parameter takes: {@code stars=4}, {@code facilities=pool},
 * {@code maxDistanceM=1000}
 * @param name display name; null for numeric options, which the client formats ("4.5+", "within 1 km")
 * @param category heading a facility or room feature is listed under; null for other options
 */
public record FilterOption(String value, String name, String category, long hotels) {
}
