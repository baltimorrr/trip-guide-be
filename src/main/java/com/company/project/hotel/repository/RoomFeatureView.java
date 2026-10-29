package com.company.project.hotel.repository;

/** A room feature (an amenity with scope ROOM) of a room type. */
public record RoomFeatureView(Long roomTypeId, String code, String name, String icon) {
}
