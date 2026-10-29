package com.company.project.hotel.dto;

/**
 * A facility the hotel offers.
 *
 * @param free false when it costs extra (paid parking...)
 */
public record Facility(String code, String name, String icon, boolean free) {
}
