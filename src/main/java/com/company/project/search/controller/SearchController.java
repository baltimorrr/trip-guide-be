package com.company.project.search.controller;

import com.company.project.common.dto.PageResponse;
import com.company.project.search.dto.HotelSearchRequest;
import com.company.project.search.dto.HotelSearchResult;
import com.company.project.search.dto.SuggestionsResponse;
import com.company.project.search.services.SearchService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public homepage search. Every search is a plain GET whose criteria are all in the query string: the
 * browser keeps the last one (C-HOME-02) and replays it, and the server stores nothing.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;

	/** {@code ?q=da nang&limit=5}: up to {@code limit} (max 10) places and hotels. */
	@GetMapping("/suggestions")
	@SecurityRequirements // public: no bearer token in the API docs
	public SuggestionsResponse suggestions(@RequestParam String q, @RequestParam(defaultValue = "5") int limit) {
		return searchService.suggest(q, limit);
	}

	/**
	 * {@code ?destination=vn-da-nang&checkIn=2026-11-01&checkOut=2026-11-03&rooms=1&adults=2&childAges=7}.
	 * Results are ordered by user rating; a {@code sort} parameter is ignored.
	 */
	@GetMapping("/hotels")
	@SecurityRequirements // public: no bearer token in the API docs
	public PageResponse<HotelSearchResult> hotels(@Valid @ParameterObject HotelSearchRequest request,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(searchService.searchHotels(request, pageable));
	}

}
