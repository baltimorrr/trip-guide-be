package com.company.project.review.controller;

import com.company.project.common.dto.PageResponse;
import com.company.project.review.dto.HotelReview;
import com.company.project.review.services.ReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A hotel's reviews on its public page (C-HOTEL-07). */
@RestController
@RequestMapping("/api/hotels/{slug}/reviews")
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;

	/**
	 * {@code ?page=0&size=10&sort=score,desc}: published reviews, newest first by default; {@code sort} takes
	 * {@code createdAt} or {@code score} in either direction. 404 when the hotel isn't listed.
	 */
	@GetMapping
	@SecurityRequirements // public: no bearer token in the API docs
	public PageResponse<HotelReview> list(@PathVariable String slug, @ParameterObject @PageableDefault(size = 10,
			sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(reviewService.list(slug, pageable));
	}

}
