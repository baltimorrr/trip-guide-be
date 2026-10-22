package com.company.project.hotel.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Hand-written hotel queries, implemented in {@code impl/HotelRepositoryImpl}. Text parameters named
 * {@code query} must already be LIKE-escaped with backslashes (norm() keeps \, % and _ as they are). Methods
 * returning a page use a fixed order and ignore any sort in the Pageable.
 */
public interface HotelRepositoryCustom {

	/** Listed hotels with a word of the name starting with {@code query}; whole-name prefix first. */
	List<HotelSuggestionView> suggest(String query, int limit);

	/** Cards for these hotels, in no particular order. */
	List<HotelCardView> findCardsByIdIn(Collection<Long> ids);

	/** Best-rated listed hotels inside a destination. */
	List<HotelCardView> findTopInDestination(long destinationId, int limit);

	/** Ids of listed hotels inside the destination that can host the stay, best user rating first. */
	Page<Long> findAvailableInDestination(long destinationId, StayRequirement stay, Pageable pageable);

	/** The hotel's id when it is listed and can host the stay, else an empty page. */
	Page<Long> findAvailableHotel(long hotelId, StayRequirement stay, Pageable pageable);

}
