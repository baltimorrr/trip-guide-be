package com.company.project.cart.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.company.project.cart.config.CartProperties;
import com.company.project.cart.dto.CartItemResponse;
import com.company.project.cart.dto.CartItemStatus;
import com.company.project.cart.dto.CartResponse;
import com.company.project.cart.mapper.CartMapper;
import com.company.project.cart.model.RoomGuests;
import com.company.project.cart.repository.CartItemRepository;
import com.company.project.cart.repository.CartLineView;
import com.company.project.cart.repository.CartRepository;
import com.company.project.hotel.mapper.HotelMapper;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.hotel.repository.RoomRateView;
import com.company.project.hotel.repository.RoomTypeRepository;
import com.company.project.hotel.repository.StayRequirement;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.rate.repository.RatePlanRepository;
import com.company.project.search.config.SearchProperties;
import com.company.project.tax.repository.TaxRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Plain unit tests of the priced read: the repositories are mocks, the mappers are the real ones. */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

	static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);

	static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

	static final long HOTEL = 5;

	@Mock
	CartRepository carts;

	@Mock
	CartItemRepository cartItems;

	@Mock
	HotelRepository hotels;

	@Mock
	RoomTypeRepository roomTypes;

	@Mock
	RatePlanRepository ratePlans;

	@Mock
	TaxRuleRepository taxRules;

	CartServiceImpl cartService;

	@BeforeEach
	void setUp() {
		cartService = new CartServiceImpl(carts, cartItems, hotels, roomTypes, ratePlans, taxRules,
				new CartMapper(new HotelMapper()), new CartProperties(20, "-"), new SearchProperties(30, 365, 8, 30, 10, 10));
	}

	@Test
	void anEmptyCartReadsOnlyItsLines() {
		when(cartItems.findLines(7)).thenReturn(List.of());

		assertThat(cartService.cart(7).groups()).isEmpty();
		verifyNoInteractions(hotels, roomTypes, ratePlans, taxRules);
	}

	@Test
	void linesWhoseCheckInPassedAreNotPriced() {
		LocalDate past = LocalDate.now().minusDays(5);
		lines(line(1, 10, 100, past, past.plusDays(2), 1));
		decorations(100);

		CartResponse cart = cartService.cart(7);

		assertThat(statuses(cart)).containsExactly(Map.entry(1L, CartItemStatus.CHECK_IN_PASSED));
		verify(hotels, never()).findRoomRates(anyLong(), any());
	}

	@Test
	void linesOfOneStayAndGuestsShareOneRoomListCall() {
		lines(line(1, 10, 100, CHECK_IN, CHECK_OUT, 1), line(2, 10, 101, CHECK_IN, CHECK_OUT, 1));
		rates(CHECK_IN, CHECK_OUT, rate(10, 100, 5), rate(10, 101, 5));
		decorations(100, 101);

		assertThat(statuses(cartService.cart(7))).containsOnly(Map.entry(1L, CartItemStatus.AVAILABLE),
				Map.entry(2L, CartItemStatus.AVAILABLE));
		verify(hotels, times(1)).findRoomRates(eq(HOTEL), any());
	}

	@Test
	void notEnoughRoomsCountsTheGroupsLinesOfTheSameRoomTypeOnly() {
		LocalDate later = CHECK_OUT.plusDays(3);
		lines(line(1, 10, 100, CHECK_IN, CHECK_OUT, 2), line(2, 10, 101, CHECK_IN, CHECK_OUT, 2),
				line(3, 11, 102, CHECK_IN, CHECK_OUT, 2), line(4, 10, 100, later, later.plusDays(1), 2));
		rates(CHECK_IN, CHECK_OUT, rate(10, 100, 3), rate(10, 101, 3), rate(11, 102, 2));
		rates(later, later.plusDays(1), rate(10, 100, 3));
		decorations(100, 101, 102);

		assertThat(statuses(cartService.cart(7))).containsOnly(Map.entry(1L, CartItemStatus.NOT_ENOUGH_ROOMS),
				Map.entry(2L, CartItemStatus.NOT_ENOUGH_ROOMS), Map.entry(3L, CartItemStatus.AVAILABLE),
				Map.entry(4L, CartItemStatus.AVAILABLE));
	}

	private void lines(CartLineView... lines) {
		when(cartItems.findLines(7)).thenReturn(List.of(lines));
	}

	private void rates(LocalDate checkIn, LocalDate checkOut, RoomRateView... rates) {
		when(hotels.findRoomRates(HOTEL, new StayRequirement(checkIn, checkOut, 1, 2, 0))).thenReturn(List.of(rates));
	}

	private void decorations(long... ratePlanIds) {
		when(ratePlans.findOfferDetails(any())).thenReturn(Arrays.stream(ratePlanIds)
			.boxed()
			.map(id -> new RateOfferView(id, "Rate " + id, "Room", "RO", "Room only", "Free", true, null,
					"Asia/Ho_Chi_Minh", LocalTime.of(14, 0)))
			.toList());
		when(hotels.findCardsByIdIn(any())).thenReturn(List.of(new HotelCardView(HOTEL, "slug", "Hotel", "hotel", null,
				null, null, "Đà Nẵng", "VN", null, null, BigDecimal.ZERO, 0, null)));
		when(hotels.findListedIds(any())).thenReturn(List.of(HOTEL));
		when(taxRules.findCountriesWithTaxesOnTop(eq(Set.of("VN")), any(), any())).thenReturn(Set.of());
	}

	private static CartLineView line(long itemId, long roomTypeId, long ratePlanId, LocalDate checkIn,
			LocalDate checkOut, int quantity) {
		return new CartLineView(itemId, HOTEL, roomTypeId, ratePlanId, "VND", checkIn, checkOut, quantity,
				new RoomGuests(2, List.of()), 2_000_000, "VND");
	}

	private static RoomRateView rate(long roomTypeId, long ratePlanId, int roomsLeft) {
		return new RoomRateView(roomTypeId, ratePlanId, roomsLeft, true, 2_000_000, List.of(1_000_000L, 1_000_000L));
	}

	private static Map<Long, CartItemStatus> statuses(CartResponse cart) {
		return cart.groups()
			.stream()
			.flatMap(group -> group.items().stream())
			.collect(Collectors.toMap(CartItemResponse::id, CartItemResponse::status, (a, b) -> a, LinkedHashMap::new));
	}

}
