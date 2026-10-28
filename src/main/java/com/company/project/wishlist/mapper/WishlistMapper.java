package com.company.project.wishlist.mapper;

import com.company.project.hotel.repository.HotelCardView;
import com.company.project.wishlist.dto.WishlistHotel;
import com.company.project.wishlist.repository.WishlistEntryView;
import org.springframework.stereotype.Component;

/** Combines a wishlist entry with its hotel's card. */
@Component
public class WishlistMapper {

	public WishlistHotel toResponse(HotelCardView card, WishlistEntryView entry) {
		return new WishlistHotel(card.id(), card.slug(), card.name(), card.accommodationType(), card.starRating(),
				card.coverPhotoUrl(), card.district(), card.city(), card.countryCode(), card.googleRating(),
				card.googleRatingCount(), card.userRatingAvg(), card.userRatingCount(), card.distanceToCenterM(),
				entry.available(), entry.savedAt());
	}

}
