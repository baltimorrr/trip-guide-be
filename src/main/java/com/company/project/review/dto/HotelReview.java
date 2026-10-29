package com.company.project.review.dto;

import java.time.Instant;

import com.company.project.review.model.TravellerType;

/**
 * A published review on the hotel page (C-HOTEL-07). Who wrote it is not shown.
 *
 * @param score 1 to 10
 * @param travellerType who the reviewer travelled with; null when not given
 * @param ownerReply the hotel's answer, null when there is none
 */
public record HotelReview(Long id, int score, String title, String body, TravellerType travellerType,
		Instant createdAt, String ownerReply, Instant ownerRepliedAt) {
}
