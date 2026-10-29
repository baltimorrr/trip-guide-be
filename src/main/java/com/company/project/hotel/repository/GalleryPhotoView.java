package com.company.project.hotel.repository;

/**
 * A photo of the hotel page's gallery.
 *
 * @param category what it shows (pool, lobby...); {@code room} for a room photo stored without one
 * @param roomTypeId null for a photo of the hotel itself
 */
public record GalleryPhotoView(String url, String category, String caption, Long roomTypeId) {
}
