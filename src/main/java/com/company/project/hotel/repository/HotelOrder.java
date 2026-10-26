package com.company.project.hotel.repository;

/**
 * Order of a page of search results.
 *
 * @param sort null = the default order: best user rating first, then most reviews
 * @param ratingMinReviews reviews a hotel needs to be ranked by its rating under {@link HotelSort#RATING}
 */
public record HotelOrder(HotelSort sort, int ratingMinReviews) {
}
