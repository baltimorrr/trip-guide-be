package com.company.project.search.services.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.destination.repository.DestinationRepository;
import com.company.project.hotel.model.ListingStatus;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelOfferView;
import com.company.project.hotel.repository.HotelPhotoView;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.hotel.repository.OfferLineView;
import com.company.project.hotel.repository.StayRequirement;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.rate.repository.RatePlanRepository;
import com.company.project.search.config.SearchProperties;
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

	// UTC-12 is the last place where any given date is still "today". Comparing check-in with the date
	// there never rejects a traveller's own today, wherever they are.
	private static final ZoneOffset LATEST_TIME_ZONE = ZoneOffset.ofHours(-12);

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
		boolean byDestination = StringUtils.hasText(request.destination());
		if (byDestination == StringUtils.hasText(request.hotel())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Choose either a destination or a hotel");
		}
		SearchCriteria criteria = SearchCriteria.of(request.checkIn(), request.checkOut(), request.rooms(),
				request.adults(), request.childAges(), LocalDate.now(LATEST_TIME_ZONE), limits);
		StayRequirement stay = new StayRequirement(criteria.checkIn(), criteria.checkOut(), criteria.rooms(),
				criteria.adultsPerRoom(), criteria.childrenPerRoom());
		Page<HotelOfferView> offers;
		if (byDestination) {
			long destinationId = destinations.findBySlugAndDeletedAtIsNull(request.destination())
				.orElseThrow(() -> new ResourceNotFoundException("Destination", request.destination()))
				.getId();
			offers = hotels.findAvailableInDestination(destinationId, stay, pageable);
		}
		else {
			long hotelId = hotels.findBySlugAndStatusAndDeletedAtIsNull(request.hotel(), ListingStatus.ACTIVE)
				.orElseThrow(() -> new ResourceNotFoundException("Hotel", request.hotel()))
				.getId();
			offers = hotels.findAvailableHotel(hotelId, stay, pageable);
		}
		return new PageImpl<>(results(offers.getContent(), criteria), pageable, offers.getTotalElements());
	}

	// The priced availability query decides which hotels, in what order and with which offer; JPQL then loads
	// their cards, photos, rate descriptions and whether their countries add taxes on top.
	private List<HotelSearchResult> results(List<HotelOfferView> offers, SearchCriteria criteria) {
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
		Set<String> taxedOnTop = taxRules.findCountriesWithTaxesOnTop(countries, criteria.checkIn(),
				criteria.checkOut().minusDays(1));
		Instant now = Instant.now();
		return offers.stream().map(offer -> {
			HotelCardView card = cards.get(offer.hotelId());
			StayOffer stayOffer = searchMapper.toOffer(offer, rates, criteria,
					!taxedOnTop.contains(card.countryCode()), now);
			return searchMapper.toResult(card, photos.getOrDefault(offer.hotelId(), List.of()), stayOffer);
		}).toList();
	}

	// Typed text is matched with LIKE; escape its wildcards so "%" or "_" match only themselves.
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
