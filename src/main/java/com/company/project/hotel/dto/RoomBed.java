package com.company.project.hotel.dto;

/** {@code quantity} beds of one type: single, double, queen, king or twin. */
public record RoomBed(String code, String name, int quantity) {
}
