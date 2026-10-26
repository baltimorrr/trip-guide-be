package com.company.project.search.services;

import com.company.project.search.dto.HotelFilterCounts;
import com.company.project.search.dto.HotelSearchRequest;
import com.company.project.search.dto.HotelSearchResult;
import com.company.project.search.dto.SuggestionsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Homepage search. Stateless: nothing about a search is stored, so the browser keeps the last search
 * itself and simply sends the same request again.
 */
public interface SearchService {

	/** Places and hotels whose names have a word starting with {@code query}, ignoring accents and case. */
	SuggestionsResponse suggest(String query, int limit);

	/**
	 * Hotels that pass the filters (C-SRCH-02) and can sell the whole stay, each with its cheapest allowed offer
	 * (C-SRCH-01), in the order the Pageable's sort names (C-SRCH-03). Throws BusinessException (400) for invalid
	 * criteria or sort and ResourceNotFoundException (404) for an unknown destination or hotel.
	 */
	Page<HotelSearchResult> searchHotels(HotelSearchRequest request, Pageable pageable);

	/**
	 * Every filter option with the number of hotels the result list would show if it were picked, plus the price
	 * range per night (C-SRCH-02). Same parameters and errors as {@link #searchHotels}.
	 */
	HotelFilterCounts filterCounts(HotelSearchRequest request);

}
