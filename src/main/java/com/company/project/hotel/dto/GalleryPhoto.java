package com.company.project.hotel.dto;

/**
 * A photo of the gallery, which can be browsed by {@code category}.
 *
 * @param category what it shows (pool, lobby, room...); null when the hotel gave none
 * @param roomTypeId the room type it shows, null for a photo of the hotel itself
 */
public record GalleryPhoto(String url, String category, String caption, Long roomTypeId) {
}
