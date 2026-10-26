package com.company.project.hotel.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Hand-written hotel queries, implemented in {@code impl/HotelRepositoryImpl}. Text parameters named
 * {@code query} must already be LIKE-escaped with backslashes (norm() keeps \, % and _ as they are). Methods
 * returning a page take their order from {@link HotelOrder} and ignore any sort in the Pageable.
 */
public interface HotelRepositoryCustom {

	/** Listed hotels with a word of the name starting with {@code query}; whole-name prefix first. */
	List<HotelSuggestionView> suggest(String query, int limit);

	/** Cards for these hotels, in no particular order. */
	List<HotelCardView> findCardsByIdIn(Collection<Long> ids);

	/** Best-rated listed hotels inside a destination. */
	List<HotelCardView> findTopInDestination(long destinationId, int limit);

	/** Photos of these hotels themselves (no room photos), per hotel cover first, then in their order. */
	List<HotelPhotoView> findHotelPhotos(Collection<Long> hotelIds);

	/** The selling currency of most listed hotels inside the destination (ties: alphabetical); empty without any. */
	Optional<String> findMainCurrencyInDestination(long destinationId);

	/**
	 * Listed hotels inside the destination that pass the filter and can sell the stay, each with its cheapest offer
	 * among the rate plans and room types the filter allows.
	 */
	Page<HotelOfferView> findAvailableInDestination(long destinationId, StayRequirement stay, HotelFilter filter,
			HotelOrder order, Pageable pageable);

	/** Like {@link #findAvailableInDestination} for one hotel: a page with it, or an empty page. */
	Page<HotelOfferView> findAvailableHotel(long hotelId, StayRequirement stay, HotelFilter filter, HotelOrder order,
			Pageable pageable);

	/**
	 * For each filter option, how many listed hotels inside the destination would show if that option were picked
	 * (C-SRCH-02). Within an OR group (stars, accommodation type, board, cancellation, bed type) the group's own
	 * selection is ignored; facilities and room features narrow the current selection; numeric filters count each
	 * fixed value. Plus the offer totals before the price filter.
	 */
	FilterCountsView findFilterCountsInDestination(long destinationId, StayRequirement stay, HotelFilter filter);

	/** Like {@link #findFilterCountsInDestination} for one hotel: every count is 0 or 1. */
	FilterCountsView findFilterCountsForHotel(long hotelId, StayRequirement stay, HotelFilter filter);

	/** The options of the filters backed by reference data, in display order. */
	Map<FilterGroup, List<FilterOptionView>> findFilterOptions();

}
