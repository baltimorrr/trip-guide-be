package com.company.project.wishlist.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.hotel.model.ListingStatus;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.wishlist.dto.WishlistHotel;
import com.company.project.wishlist.mapper.WishlistMapper;
import com.company.project.wishlist.repository.WishlistEntryView;
import com.company.project.wishlist.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Plain unit tests: the repositories are mocks, the mapper is the real one. */
@ExtendWith(MockitoExtension.class)
class WishlistServiceImplTest {

	static final long USER_ID = 7L;

	@Mock
	WishlistRepository wishlists;

	@Mock
	HotelRepository hotels;

	WishlistServiceImpl wishlistService;

	@BeforeEach
	void setUp() {
		wishlistService = new WishlistServiceImpl(wishlists, hotels, new WishlistMapper());
	}

	@Test
	void saveRejectsAHotelThatIsNotListed() {
		when(hotels.existsByIdAndStatusAndDeletedAtIsNull(42L, ListingStatus.ACTIVE)).thenReturn(false);

		assertThatThrownBy(() -> wishlistService.save(USER_ID, 42L))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Hotel 42 not found");
		verify(wishlists, never()).addIfAbsent(anyLong(), anyLong(), any());
	}

	@Test
	void saveAddsAListedHotel() {
		when(hotels.existsByIdAndStatusAndDeletedAtIsNull(42L, ListingStatus.ACTIVE)).thenReturn(true);

		wishlistService.save(USER_ID, 42L);

		verify(wishlists).addIfAbsent(eq(USER_ID), eq(42L), any(Instant.class));
	}

	@Test
	void listKeepsTheWishlistOrderAndAvailability() {
		PageRequest pageable = PageRequest.of(0, 2);
		Instant newer = Instant.parse("2026-10-28T04:00:00Z");
		Instant older = Instant.parse("2026-10-27T04:00:00Z");
		when(wishlists.findPage(USER_ID, pageable)).thenReturn(new PageImpl<>(
				List.of(new WishlistEntryView(2L, newer, false), new WishlistEntryView(1L, older, true)), pageable, 5));
		when(hotels.findCardsByIdIn(List.of(2L, 1L))).thenReturn(List.of(card(1L), card(2L)));

		Page<WishlistHotel> page = wishlistService.list(USER_ID, pageable);

		assertThat(page.getContent()).extracting(WishlistHotel::id).containsExactly(2L, 1L);
		assertThat(page.getContent()).extracting(WishlistHotel::available).containsExactly(false, true);
		assertThat(page.getContent()).extracting(WishlistHotel::savedAt).containsExactly(newer, older);
		assertThat(page.getContent().getFirst().slug()).isEqualTo("hotel-2");
		assertThat(page.getTotalElements()).isEqualTo(5);
	}

	@Test
	void emptyPageSkipsTheCardQuery() {
		PageRequest pageable = PageRequest.of(3, 20);
		when(wishlists.findPage(USER_ID, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 4));

		Page<WishlistHotel> page = wishlistService.list(USER_ID, pageable);

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isEqualTo(4);
		verify(hotels, never()).findCardsByIdIn(any());
	}

	private static HotelCardView card(long id) {
		return new HotelCardView(id, "hotel-" + id, "Hotel " + id, "hotel", null, null, null, "City", "VN", null, null,
				null, 0, null);
	}

}
