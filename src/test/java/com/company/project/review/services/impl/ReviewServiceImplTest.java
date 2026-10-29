package com.company.project.review.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.hotel.model.ListingStatus;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.review.mapper.ReviewMapper;
import com.company.project.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Plain unit tests: the repositories are mocks, the mapper is the real one. */
@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

	@Mock
	HotelRepository hotels;

	@Mock
	ReviewRepository reviews;

	ReviewServiceImpl reviewService;

	@BeforeEach
	void setUp() {
		reviewService = new ReviewServiceImpl(hotels, reviews, new ReviewMapper());
	}

	@Test
	void unsortedMeansNewestFirst() {
		Pageable ordered = ReviewServiceImpl.ordered(PageRequest.of(2, 5));

		assertThat(ordered.getPageNumber()).isEqualTo(2);
		assertThat(ordered.getPageSize()).isEqualTo(5);
		assertThat(ordered.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
	}

	@Test
	void scoreTiesGoToTheNewestThenTheHigherId() {
		assertThat(ReviewServiceImpl.ordered(PageRequest.of(0, 10, Sort.by("score"))).getSort())
			.isEqualTo(Sort.by(Sort.Order.asc("score"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		assertThat(ReviewServiceImpl.ordered(PageRequest.of(0, 10, Sort.by("createdAt"))).getSort())
			.isEqualTo(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.desc("id")));
	}

	@Test
	void anotherSortPropertyIsRejectedBeforeAnyQuery() {
		assertThatThrownBy(() -> reviewService.list("slug", PageRequest.of(0, 10, Sort.by("score", "userId"))))
			.isInstanceOf(BusinessException.class)
			.hasMessage("Reviews can be sorted by createdAt or score");
		verifyNoInteractions(hotels, reviews);
	}

	@Test
	void anUnlistedHotelIsNotFoundWithoutAReviewQuery() {
		when(hotels.findBySlugAndStatusAndDeletedAtIsNull("gone", ListingStatus.ACTIVE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> reviewService.list("gone", PageRequest.of(0, 10)))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Hotel gone not found");
		verify(reviews, never()).findByHotelIdAndStatus(anyLong(), any(), any());
	}

}
