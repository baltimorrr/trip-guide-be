package com.company.project.hotel.repository;

/** A photo of the hotel itself (not of one of its rooms). */
public record HotelPhotoView(Long hotelId, String url) {
}
