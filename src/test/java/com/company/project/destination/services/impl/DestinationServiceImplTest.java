package com.company.project.destination.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.company.project.destination.dto.PopularDestinationResponse;
import com.company.project.destination.dto.PopularHotel;
import com.company.project.destination.mapper.DestinationMapper;
import com.company.project.destination.model.Destination;
import com.company.project.destination.repository.DestinationRepository;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Plain unit tests of the popular-destinations assembly: repositories are mocks, the mapper is real. */
@ExtendWith(MockitoExtension.class)
class DestinationServiceImplTest {

	@Mock
	DestinationRepository destinations;

	@Mock
	HotelRepository hotels;

	DestinationServiceImpl destinationService;

	@BeforeEach
	void setUp() {
		destinationService = new DestinationServiceImpl(destinations, hotels, new DestinationMapper());
	}

	@Test
	void fallsBackToTopCitiesWhenNothingIsFeatured() {
		Destination hue = destination(5L, "vn-hue");
		when(destinations.findFeatured(8)).thenReturn(List.of());
		when(destinations.findTopCities(8)).thenReturn(List.of(hue));
		when(hotels.findTopInDestination(5L, 4)).thenReturn(List.of());

		List<PopularDestinationResponse> popular = destinationService.popular(8, 4);

		assertThat(popular).extracting(PopularDestinationResponse::slug).containsExactly("vn-hue");
	}

	@Test
	void keepsTheFeaturedOrderWithEachDestinationsHotels() {
		Destination daNang = destination(2L, "vn-da-nang");
		Destination haNoi = destination(1L, "vn-ha-noi");
		HotelCardView beachOne = card("beach-one");
		HotelCardView beachTwo = card("beach-two");
		HotelCardView hanoiInn = card("hanoi-inn");
		when(destinations.findFeatured(2)).thenReturn(List.of(daNang, haNoi));
		when(hotels.findTopInDestination(2L, 3)).thenReturn(List.of(beachOne, beachTwo));
		when(hotels.findTopInDestination(1L, 3)).thenReturn(List.of(hanoiInn));

		List<PopularDestinationResponse> popular = destinationService.popular(2, 3);

		assertThat(popular).extracting(PopularDestinationResponse::slug).containsExactly("vn-da-nang", "vn-ha-noi");
		assertThat(popular.get(0).hotels()).extracting(PopularHotel::slug).containsExactly("beach-one", "beach-two");
		assertThat(popular.get(1).hotels()).extracting(PopularHotel::slug).containsExactly("hanoi-inn");
		verify(destinations, never()).findTopCities(anyInt());
	}

	@Test
	void clampsLimits() {
		Destination hue = destination(1L, "vn-hue");
		when(destinations.findFeatured(20)).thenReturn(List.of(hue));
		when(hotels.findTopInDestination(1L, 10)).thenReturn(List.of());

		assertThat(destinationService.popular(500, 500)).hasSize(1);
	}

	@Test
	void emptyHomepageQueriesNoHotels() {
		when(destinations.findFeatured(8)).thenReturn(List.of());
		when(destinations.findTopCities(8)).thenReturn(List.of());

		assertThat(destinationService.popular(8, 4)).isEmpty();
		verify(hotels, never()).findTopInDestination(anyLong(), anyInt());
	}

	// Build mocks before stubbing with them: Mockito rejects a when(...) nested inside another one.
	private static Destination destination(long id, String slug) {
		Destination destination = mock(Destination.class);
		when(destination.getId()).thenReturn(id);
		when(destination.getSlug()).thenReturn(slug);
		return destination;
	}

	private static HotelCardView card(String slug) {
		return new HotelCardView(1L, slug, slug, "hotel", (short) 4, null, null, "City", "VN", null, null, null, 0,
				null);
	}

}
