package com.company.project.search.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.company.project.common.dto.Money;
import com.company.project.destination.repository.DestinationSuggestionView;
import com.company.project.hotel.repository.FilterCountsView;
import com.company.project.hotel.repository.FilterGroup;
import com.company.project.hotel.repository.FilterOptionView;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelOfferView;
import com.company.project.hotel.repository.HotelSuggestionView;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.search.dto.CancellationOption;
import com.company.project.search.dto.CancellationTerms;
import com.company.project.search.dto.DestinationSuggestion;
import com.company.project.search.dto.FilterOption;
import com.company.project.search.dto.HotelFilterCounts;
import com.company.project.search.dto.HotelSearchResult;
import com.company.project.search.dto.HotelSuggestion;
import com.company.project.search.dto.OfferRoom;
import com.company.project.search.dto.PriceRange;
import com.company.project.search.dto.StayOffer;
import com.company.project.search.model.FreeCancellation;
import com.company.project.search.model.SearchCriteria;
import org.springframework.stereotype.Component;

/** Converts query projections into search responses and builds the "where is this" labels and the offers. */
@Component
public class SearchMapper {

	// The places a destination lies in: a ward's district, city and country; a country has none.
	public DestinationSuggestion toSuggestion(DestinationSuggestionView view) {
		String context = switch (view.type()) {
			case COUNTRY -> null;
			case CITY -> label(view.countryName());
			case DISTRICT -> label(view.city(), view.countryName());
			case WARD -> label(view.district(), view.city(), view.countryName());
		};
		return new DestinationSuggestion(view.slug(), view.type(), view.name(), context, view.hotelCount());
	}

	public HotelSuggestion toSuggestion(HotelSuggestionView view) {
		return new HotelSuggestion(view.slug(), view.name(), label(view.district(), view.city()));
	}

	public HotelSearchResult toResult(HotelCardView card, List<String> photoUrls, StayOffer offer) {
		return new HotelSearchResult(card.id(), card.slug(), card.name(), card.accommodationType(),
				card.starRating(), photoUrls, card.district(), card.city(), card.countryCode(), card.googleRating(),
				card.googleRatingCount(), card.userRatingAvg(), card.userRatingCount(), card.distanceToCenterM(),
				offer);
	}

	/**
	 * The offer of one hotel for the searched stay, its lines described by {@code rates} (by rate plan id).
	 * {@code now} decides whether free cancellation is still possible.
	 */
	public StayOffer toOffer(HotelOfferView offer, Map<Long, RateOfferView> rates, SearchCriteria criteria,
			boolean taxesIncluded, Instant now) {
		List<OfferRoom> lines = offer.lines().stream().map(line -> {
			RateOfferView rate = rates.get(line.ratePlanId());
			OffsetDateTime freeUntil = FreeCancellation.until(criteria.checkIn(), rate.checkInFrom(),
					ZoneId.of(rate.hotelTimezone()), rate.refundable(), rate.penaltyFromHours(), now);
			return new OfferRoom(line.roomTypeId(), rate.roomTypeName(), line.ratePlanId(), rate.ratePlanName(),
					line.quantity(), rate.boardCode(), rate.boardName(),
					new CancellationTerms(rate.refundable(), freeUntil, rate.policyName()),
					Money.ofMinor(line.totalMinor(), line.currency()));
		}).toList();
		return new StayOffer(criteria.nights(), criteria.rooms(), Money.ofMinor(offer.totalMinor(), offer.currency()),
				taxesIncluded, lines);
	}

	/**
	 * Every option of every filter with its count: numeric options from {@link FilterGroup#optionValues()},
	 * cancellation from {@link CancellationOption}, the rest from reference data. Totals become prices per night.
	 */
	public HotelFilterCounts toFilterCounts(Map<FilterGroup, List<FilterOptionView>> referenceOptions,
			FilterCountsView counts, String currency, int nights) {
		Map<FilterGroup, List<FilterOption>> options = new EnumMap<>(FilterGroup.class);
		for (FilterGroup group : FilterGroup.values()) {
			options.put(group, optionsOf(group, referenceOptions)
				.map(view -> new FilterOption(view.value(), view.name(), view.category(), counts.hotels(group, view.value())))
				.toList());
		}
		PriceRange priceRange = counts.minTotalMinor() == null ? null
				: new PriceRange(Money.ofMinor(Math.floorDiv(counts.minTotalMinor(), nights), currency),
						Money.ofMinor(Math.ceilDiv(counts.maxTotalMinor(), nights), currency));
		return new HotelFilterCounts(currency, priceRange, options.get(FilterGroup.STARS),
				options.get(FilterGroup.GOOGLE_RATING), options.get(FilterGroup.USER_RATING),
				options.get(FilterGroup.ACCOMMODATION_TYPE), options.get(FilterGroup.FACILITIES),
				options.get(FilterGroup.DISTANCE), options.get(FilterGroup.BOARD_TYPE),
				options.get(FilterGroup.CANCELLATION), options.get(FilterGroup.BED_TYPE),
				options.get(FilterGroup.ROOM_FEATURES));
	}

	private static Stream<FilterOptionView> optionsOf(FilterGroup group,
			Map<FilterGroup, List<FilterOptionView>> referenceOptions) {
		if (group == FilterGroup.CANCELLATION) {
			return Stream.of(CancellationOption.values()).map(option -> new FilterOptionView(option.name(), option.label()));
		}
		if (!group.optionValues().isEmpty()) {
			return group.optionValues().stream().map(value -> new FilterOptionView(value, null));
		}
		return referenceOptions.getOrDefault(group, List.of()).stream();
	}

	// "Hải Châu, Đà Nẵng, Vietnam" from the parts that exist; null when none does.
	private static String label(String... parts) {
		String label = Stream.of(parts).filter(Objects::nonNull).collect(Collectors.joining(", "));
		return label.isEmpty() ? null : label;
	}

}
