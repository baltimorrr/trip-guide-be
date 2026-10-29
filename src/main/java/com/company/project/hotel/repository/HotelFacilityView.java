package com.company.project.hotel.repository;

/** A facility the hotel offers, under its amenity group's heading. */
public record HotelFacilityView(String groupName, String code, String name, String icon, boolean free) {
}
