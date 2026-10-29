package com.company.project.review.repository;

import com.company.project.review.model.Review;
import com.company.project.review.model.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reviews. Spring Data generates CRUD and the derived query; nothing is hand-written. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

	/** A hotel's reviews with this status, in the Pageable's order. */
	Page<Review> findByHotelIdAndStatus(Long hotelId, ReviewStatus status, Pageable pageable);

}
