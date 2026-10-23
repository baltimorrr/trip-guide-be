package com.company.project.search.services;

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
	 * Hotels that can sell the whole stay, each with its cheapest offer (C-SRCH-01). Throws BusinessException (400)
	 * for invalid criteria and ResourceNotFoundException (404) for an unknown destination or hotel.
	 */
	Page<HotelSearchResult> searchHotels(HotelSearchRequest request, Pageable pageable);

}
