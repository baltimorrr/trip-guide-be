package com.company.project.hotel.repository;

import java.math.BigDecimal;
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

	/** Those of these hotels that are listed, in no particular order. */
	List<Long> findListedIds(Collection<Long> ids);

	/** Best-rated listed hotels inside a destination. */
	List<HotelCardView> findTopInDestination(long destinationId, int limit);

	/** Photos of these hotels themselves (no room photos), per hotel cover first, then in their order. */
	List<HotelPhotoView> findHotelPhotos(Collection<Long> hotelIds);

	/** The listed hotel with this slug, as the hotel page shows it; empty when there is none. */
	Optional<HotelDetailView> findDetail(String slug);

	/**
	 * The hotel page's gallery: photos of the hotel itself (cover first, then in their order), then photos of its room
	 * types that are on sale.
	 */
	List<GalleryPhotoView> findGallery(long hotelId);

	/** The hotel's facilities (HOTEL-scope amenities) by group, then name. */
	List<HotelFacilityView> findFacilities(long hotelId);

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
	 * The hotel page's room list (C-HOTEL-04): every rate plan of the listed hotel that can sell the stay, by the same
	 * rules as the search, whether or not its room type fits the guests. Fitting room types first, then cheapest
	 * first; empty when the hotel can sell nothing for these nights.
	 */
	List<RoomRateView> findRoomRates(long hotelId, StayRequirement stay);

	/** The commission, in percent, the platform takes on the hotel's bookings: its partner's rate. */
	BigDecimal findCommissionRate(long hotelId);

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
