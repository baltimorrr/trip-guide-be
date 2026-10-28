package com.company.project.wishlist.services.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.hotel.model.ListingStatus;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.wishlist.dto.WishlistHotel;
import com.company.project.wishlist.mapper.WishlistMapper;
import com.company.project.wishlist.repository.WishlistEntryView;
import com.company.project.wishlist.repository.WishlistRepository;
import com.company.project.wishlist.services.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistServiceImpl implements WishlistService {

	private final WishlistRepository wishlists;

	private final HotelRepository hotels;

	private final WishlistMapper wishlistMapper;

	// The entries decide which hotels, in what order and whether each is still listed; the card query then loads
	// them. It doesn't filter by listing, and the foreign key keeps every saved hotel's row, so each entry has a card.
	@Override
	public Page<WishlistHotel> list(long userId, Pageable pageable) {
		Page<WishlistEntryView> entries = wishlists.findPage(userId, pageable);
		if (entries.isEmpty()) {
			// A page past the end still reports the totals.
			return new PageImpl<>(List.of(), pageable, entries.getTotalElements());
		}
		List<Long> ids = entries.map(WishlistEntryView::hotelId).getContent();
		Map<Long, HotelCardView> cards = hotels.findCardsByIdIn(ids).stream()
			.collect(Collectors.toMap(HotelCardView::id, Function.identity()));
		return entries.map(entry -> wishlistMapper.toResponse(cards.get(entry.hotelId()), entry));
	}

	@Override
	@Transactional
	public void save(long userId, long hotelId) {
		if (!hotels.existsByIdAndStatusAndDeletedAtIsNull(hotelId, ListingStatus.ACTIVE)) {
			throw new ResourceNotFoundException("Hotel", hotelId);
		}
		wishlists.addIfAbsent(userId, hotelId, Instant.now());
	}

	@Override
	@Transactional
	public void remove(long userId, long hotelId) {
		wishlists.remove(userId, hotelId);
	}

}
