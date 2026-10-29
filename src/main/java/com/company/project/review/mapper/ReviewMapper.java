package com.company.project.review.mapper;

import com.company.project.review.dto.HotelReview;
import com.company.project.review.model.Review;
import org.springframework.stereotype.Component;

/** Converts reviews into responses. */
@Component
public class ReviewMapper {

	public HotelReview toResponse(Review review) {
		return new HotelReview(review.getId(), review.getScore(), review.getTitle(), review.getBody(),
				review.getTravellerType(), review.getCreatedAt(), review.getOwnerReply(), review.getOwnerRepliedAt());
	}

}
