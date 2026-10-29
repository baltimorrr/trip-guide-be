package com.company.project.hotel.repository;

/** {@code quantity} beds of one type in a room type. */
public record RoomBedView(Long roomTypeId, String code, String name, short quantity) {
}
