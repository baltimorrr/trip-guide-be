package com.company.project.review.services;

import com.company.project.review.dto.HotelReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Reviews as travellers read them. Writing them comes with completed stays (C-MYBK-05). */
public interface ReviewService {

	/**
	 * A listed hotel's published reviews (C-HOTEL-07), newest first unless the Pageable sorts by {@code createdAt} or
	 * {@code score}; ties go to the newest. Throws BusinessException (400) for any other sort property and
	 * ResourceNotFoundException (404) for a hotel that doesn't exist or isn't listed.
	 */
	Page<HotelReview> list(String hotelSlug, Pageable pageable);

}
