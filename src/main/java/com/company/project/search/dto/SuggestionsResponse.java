package com.company.project.search.dto;

import java.util.List;

/** Autocomplete for the location field: places first, then hotels, each list ranked best first. */
public record SuggestionsResponse(List<DestinationSuggestion> destinations, List<HotelSuggestion> hotels) {
}
