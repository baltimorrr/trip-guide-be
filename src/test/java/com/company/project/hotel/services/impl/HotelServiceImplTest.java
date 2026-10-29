package com.company.project.hotel.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.hotel.dto.RoomList;
import com.company.project.hotel.dto.StayRequest;
import com.company.project.hotel.mapper.HotelMapper;
import com.company.project.hotel.model.Address;
import com.company.project.hotel.model.Hotel;
import com.company.project.hotel.model.ListingStatus;
import com.company.project.hotel.repository.HotelPolicyRepository;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.hotel.repository.RoomTypeRepository;
import com.company.project.rate.repository.RatePlanRepository;
import com.company.project.search.config.SearchProperties;
import com.company.project.tax.repository.TaxRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Plain unit tests: the repositories are mocks, the mapper is the real one. */
@ExtendWith(MockitoExtension.class)
class HotelServiceImplTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);

	static final StayRequest STAY = new StayRequest(CHECK_IN, CHECK_IN.plusDays(2), 1, 2, List.of());

	@Mock
	HotelRepository hotels;

	@Mock
	HotelPolicyRepository hotelPolicies;

	@Mock
	RoomTypeRepository roomTypes;

	@Mock
	RatePlanRepository ratePlans;

	@Mock
	TaxRuleRepository taxRules;

	HotelServiceImpl hotelService;

	@BeforeEach
	void setUp() {
		hotelService = new HotelServiceImpl(hotels, hotelPolicies, roomTypes, ratePlans, taxRules, new HotelMapper(),
				new SearchProperties(30, 365, 8, 30, 10, 10));
	}

	@Test
	void nothingSellableSkipsTheRoomTypeAndRateQueries() {
		listedHotel();
		when(hotels.findRoomRates(eq(5L), any())).thenReturn(List.of());
		when(taxRules.findCountriesWithTaxesOnTop(Set.of("VN"), CHECK_IN, CHECK_IN.plusDays(1))).thenReturn(Set.of("VN"));

		RoomList list = hotelService.rooms("slug", STAY);

		assertThat(list.roomTypes()).isEmpty();
		assertThat(list.currency()).isEqualTo("VND");
		assertThat(list.taxesIncluded()).isFalse();
		assertThat(list.nights()).isEqualTo(2);
		verifyNoInteractions(roomTypes, ratePlans);
	}

	@Test
	void anUnlistedHotelIsNotFoundBeforeAnythingIsPriced() {
		when(hotels.findBySlugAndStatusAndDeletedAtIsNull("gone", ListingStatus.ACTIVE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> hotelService.rooms("gone", STAY)).isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Hotel gone not found");
		verify(hotels, never()).findRoomRates(anyLong(), any());
	}

	@Test
	void aStayTheSearchWouldRejectIsRejectedBeforeAnythingIsPriced() {
		listedHotel();
		StayRequest tooManyRooms = new StayRequest(CHECK_IN, CHECK_IN.plusDays(2), 9, 9, List.of());

		assertThatThrownBy(() -> hotelService.rooms("slug", tooManyRooms)).isInstanceOf(BusinessException.class)
			.hasMessage("Rooms cannot exceed 8");
		verify(hotels, never()).findRoomRates(anyLong(), any());
	}

	// Entities are read-only and built only by JPA, so the test mocks the one it needs. A rejected stay never reads
	// it, hence lenient.
	private void listedHotel() {
		Hotel hotel = mock(Hotel.class);
		Address address = mock(Address.class);
		lenient().when(hotel.getId()).thenReturn(5L);
		lenient().when(hotel.getCurrency()).thenReturn("VND");
		lenient().when(hotel.getAddress()).thenReturn(address);
		lenient().when(address.getCountryCode()).thenReturn("VN");
		when(hotels.findBySlugAndStatusAndDeletedAtIsNull("slug", ListingStatus.ACTIVE)).thenReturn(Optional.of(hotel));
	}

}
