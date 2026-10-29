package com.company.project.review.services.impl;

import java.util.Set;

import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.hotel.model.ListingStatus;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.review.dto.HotelReview;
import com.company.project.review.mapper.ReviewMapper;
import com.company.project.review.model.ReviewStatus;
import com.company.project.review.repository.ReviewRepository;
import com.company.project.review.services.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

	static final Set<String> SORTABLE = Set.of("createdAt", "score");

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final HotelRepository hotels;

	private final ReviewRepository reviews;

	private final ReviewMapper reviewMapper;

	@Override
	public Page<HotelReview> list(String hotelSlug, Pageable pageable) {
		Pageable ordered = ordered(pageable);
		long hotelId = hotels.findBySlugAndStatusAndDeletedAtIsNull(hotelSlug, ListingStatus.ACTIVE)
			.orElseThrow(() -> new ResourceNotFoundException("Hotel", hotelSlug))
			.getId();
		return reviews.findByHotelIdAndStatus(hotelId, ReviewStatus.PUBLISHED, ordered).map(reviewMapper::toResponse);
	}

	// Only the columns a traveller sorts by; then the newest first and the id, so pages never overlap.
	static Pageable ordered(Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (!SORTABLE.contains(order.getProperty())) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, "Reviews can be sorted by createdAt or score");
			}
		}
		Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : NEWEST_FIRST;
		if (sort.getOrderFor("createdAt") == null) {
			sort = sort.and(NEWEST_FIRST);
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				sort.and(Sort.by(Sort.Direction.DESC, "id")));
	}

}
