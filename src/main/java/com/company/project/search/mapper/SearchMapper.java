package com.company.project.search.mapper;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.company.project.destination.repository.DestinationSuggestionView;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelSuggestionView;
import com.company.project.search.dto.DestinationSuggestion;
import com.company.project.search.dto.HotelSearchResult;
import com.company.project.search.dto.HotelSuggestion;
import org.springframework.stereotype.Component;

/** Converts query projections into search responses and builds the "where is this" labels. */
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

	public HotelSearchResult toResult(HotelCardView card) {
		return new HotelSearchResult(card.id(), card.slug(), card.name(), card.accommodationType(),
				card.starRating(), card.coverPhotoUrl(), card.district(), card.city(), card.countryCode(),
				card.googleRating(), card.googleRatingCount(), card.userRatingAvg(), card.userRatingCount(),
				card.distanceToCenterM());
	}

	// "Hải Châu, Đà Nẵng, Vietnam" from the parts that exist; null when none does.
	private static String label(String... parts) {
		String label = Stream.of(parts).filter(Objects::nonNull).collect(Collectors.joining(", "));
		return label.isEmpty() ? null : label;
	}

}
