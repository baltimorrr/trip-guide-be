package com.company.project.search.services.impl;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.company.project.common.dto.Money;
import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.destination.repository.DestinationRepository;
import com.company.project.hotel.model.Hotel;
import com.company.project.hotel.model.ListingStatus;
import com.company.project.hotel.repository.FilterCountsView;
import com.company.project.hotel.repository.FilterGroup;
import com.company.project.hotel.repository.FilterOptionView;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelFilter;
import com.company.project.hotel.repository.HotelOfferView;
import com.company.project.hotel.repository.HotelOrder;
import com.company.project.hotel.repository.HotelPhotoView;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.hotel.repository.HotelSort;
import com.company.project.hotel.repository.OfferLineView;
import com.company.project.hotel.repository.StayRequirement;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.rate.repository.RatePlanRepository;
import com.company.project.search.config.SearchProperties;
import com.company.project.search.dto.HotelFilterCounts;
import com.company.project.search.dto.HotelSearchRequest;
import com.company.project.search.dto.HotelSearchResult;
import com.company.project.search.dto.StayOffer;
import com.company.project.search.dto.SuggestionsResponse;
import com.company.project.search.mapper.SearchMapper;
import com.company.project.search.model.SearchCriteria;
import com.company.project.search.services.SearchService;
import com.company.project.tax.repository.TaxRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

	static final int MIN_QUERY_LENGTH = 2;

	static final int MAX_QUERY_LENGTH = 100;

	static final int MAX_SUGGESTIONS = 10;

	static final int MAX_CARD_PHOTOS = 10;

	private final DestinationRepository destinations;

	private final HotelRepository hotels;

	private final RatePlanRepository ratePlans;

	private final TaxRuleRepository taxRules;

	private final SearchMapper searchMapper;

	private final SearchProperties limits;

	@Override
	public SuggestionsResponse suggest(String query, int limit) {
		String trimmed = query.trim();
		if (trimmed.length() < MIN_QUERY_LENGTH || trimmed.length() > MAX_QUERY_LENGTH) {
			throw new BusinessException(HttpStatus.BAD_REQUEST,
					"Search text must be %d to %d characters".formatted(MIN_QUERY_LENGTH, MAX_QUERY_LENGTH));
		}
		int size = Math.clamp(limit, 1, MAX_SUGGESTIONS);
		return new SuggestionsResponse(
				destinations.suggest(trimmed, size).stream().map(searchMapper::toSuggestion).toList(),
				hotels.suggest(escapeLike(trimmed), size).stream().map(searchMapper::toSuggestion).toList());
	}

	@Override
	public Page<HotelSearchResult> searchHotels(HotelSearchRequest request, Pageable pageable) {
		HotelOrder order = new HotelOrder(HotelSort.from(pageable.getSort()), limits.ratingSortMinReviews());
		Optional<PreparedSearch> prepared = prepare(request);
		if (prepared.isEmpty()) {
			return Page.empty(pageable);
		}
		PreparedSearch search = prepared.get();
		Page<HotelOfferView> offers = search.byDestination()
				? hotels.findAvailableInDestination(search.locationId(), search.stay(), search.filter(), order, pageable)
				: hotels.findAvailableHotel(search.locationId(), search.stay(), search.filter(), order, pageable);
		return new PageImpl<>(results(offers.getContent(), search), pageable, offers.getTotalElements());
	}

	@Override
	public HotelFilterCounts filterCounts(HotelSearchRequest request) {
		Optional<PreparedSearch> prepared = prepare(request);
		Map<FilterGroup, List<FilterOptionView>> options = hotels.findFilterOptions();
		if (prepared.isEmpty()) {
			// No currency and no price range; the night count only divides price totals, of which there are none.
			return searchMapper.toFilterCounts(options, FilterCountsView.EMPTY, null, 1);
		}
		PreparedSearch search = prepared.get();
		FilterCountsView counts = search.byDestination()
				? hotels.findFilterCountsInDestination(search.locationId(), search.stay(), search.filter())
				: hotels.findFilterCountsForHotel(search.locationId(), search.stay(), search.filter());
		return searchMapper.toFilterCounts(options, counts, search.filter().priceCurrency(), search.criteria().nights());
	}

	/**
	 * Checks the request and resolves its location and price currency.
	 * @return empty when the destination has no listed hotel and the request names no currency: nothing can match
	 */
	private Optional<PreparedSearch> prepare(HotelSearchRequest request) {
		boolean byDestination = StringUtils.hasText(request.destination());
		if (byDestination == StringUtils.hasText(request.hotel())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose either a destination or a hotel");
		}
		SearchCriteria criteria = SearchCriteria.of(request.checkIn(), request.checkOut(), request.rooms(),
				request.adults(), request.childAges(), SearchCriteria.today(), limits);
		StayRequirement stay = new StayRequirement(criteria.checkIn(), criteria.checkOut(), criteria.rooms(),
				criteria.adultsPerRoom(), criteria.childrenPerRoom());
		long locationId;
		String priceCurrency = request.currency();
		if (byDestination) {
			locationId = destinations.findBySlugAndDeletedAtIsNull(request.destination())
				.orElseThrow(() -> new ResourceNotFoundException("Destination", request.destination()))
				.getId();
			if (priceCurrency == null) {
				priceCurrency = hotels.findMainCurrencyInDestination(locationId).orElse(null);
			}
		}
		else {
			Hotel hotel = hotels.findBySlugAndStatusAndDeletedAtIsNull(request.hotel(), ListingStatus.ACTIVE)
				.orElseThrow(() -> new ResourceNotFoundException("Hotel", request.hotel()));
			locationId = hotel.getId();
			priceCurrency = priceCurrency != null ? priceCurrency : hotel.getCurrency();
		}
		if (priceCurrency == null) {
			return Optional.empty();
		}
		return Optional.of(new PreparedSearch(byDestination, locationId, criteria, stay,
				filter(request, priceCurrency, Instant.now())));
	}

	private static HotelFilter filter(HotelSearchRequest request, String priceCurrency, Instant now) {
		if (request.minPrice() != null && request.maxPrice() != null
				&& request.minPrice().compareTo(request.maxPrice()) > 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "The lowest price cannot be above the highest price");
		}
		try {
			Currency.getInstance(priceCurrency);
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Unknown currency");
		}
		Long minNightMinor = request.minPrice() == null ? null : Money.toMinor(request.minPrice(), priceCurrency);
		Long maxNightMinor = request.maxPrice() == null ? null : Money.toMinor(request.maxPrice(), priceCurrency);
		String name = StringUtils.hasText(request.name()) ? escapeLike(request.name().trim()) : null;
		return new HotelFilter(name, Set.copyOf(request.stars()), request.minGoogleRating(), request.minUserRating(),
				Set.copyOf(request.accommodationTypes()), Set.copyOf(request.facilities()), request.maxDistanceM(),
				Set.copyOf(request.boardTypes()),
				request.cancellation().stream().map(Enum::name).collect(Collectors.toSet()),
				Set.copyOf(request.bedTypes()), Set.copyOf(request.roomFeatures()), minNightMinor, maxNightMinor,
				priceCurrency, now);
	}

	// The priced availability query decides which hotels, in what order and with which offer; JPQL then loads
	// their cards, photos, rate descriptions and whether their countries add taxes on top.
	private List<HotelSearchResult> results(List<HotelOfferView> offers, PreparedSearch search) {
		if (offers.isEmpty()) {
			return List.of();
		}
		List<Long> ids = offers.stream().map(HotelOfferView::hotelId).toList();
		Map<Long, HotelCardView> cards = hotels.findCardsByIdIn(ids).stream()
			.collect(Collectors.toMap(HotelCardView::id, Function.identity()));
		Map<Long, List<String>> photos = hotels.findHotelPhotos(ids).stream()
			.collect(Collectors.groupingBy(HotelPhotoView::hotelId,
					Collectors.collectingAndThen(Collectors.mapping(HotelPhotoView::url, Collectors.toList()),
							urls -> List.copyOf(urls.subList(0, Math.min(urls.size(), MAX_CARD_PHOTOS))))));
		List<Long> ratePlanIds = offers.stream()
			.flatMap(offer -> offer.lines().stream())
			.map(OfferLineView::ratePlanId)
			.distinct()
			.toList();
		Map<Long, RateOfferView> rates = ratePlans.findOfferDetails(ratePlanIds).stream()
			.collect(Collectors.toMap(RateOfferView::ratePlanId, Function.identity()));
		Set<String> countries = cards.values().stream().map(HotelCardView::countryCode).collect(Collectors.toSet());
		SearchCriteria criteria = search.criteria();
		Set<String> taxedOnTop = taxRules.findCountriesWithTaxesOnTop(countries, criteria.checkIn(),
				criteria.checkOut().minusDays(1));
		return offers.stream().map(offer -> {
			HotelCardView card = cards.get(offer.hotelId());
			StayOffer stayOffer = searchMapper.toOffer(offer, rates, criteria,
					!taxedOnTop.contains(card.countryCode()), search.filter().now());
			return searchMapper.toResult(card, photos.getOrDefault(offer.hotelId(), List.of()), stayOffer);
		}).toList();
	}

	/** A checked search: its location (destination or hotel id), stay and filter. */
	private record PreparedSearch(boolean byDestination, long locationId, SearchCriteria criteria,
			StayRequirement stay, HotelFilter filter) {
	}

	// Typed text is matched with LIKE; escape its wildcards so "%" or "_" match only themselves.
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
