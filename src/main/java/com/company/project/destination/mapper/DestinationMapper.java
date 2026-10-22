package com.company.project.destination.mapper;

import java.util.List;

import com.company.project.destination.dto.AdminDestinationResponse;
import com.company.project.destination.dto.DestinationResponse;
import com.company.project.destination.dto.PopularDestinationResponse;
import com.company.project.destination.dto.PopularHotel;
import com.company.project.destination.model.Destination;
import com.company.project.hotel.repository.HotelCardView;
import org.springframework.stereotype.Component;

/** Converts destinations to their public and admin views. Field rules stay in Destination. */
@Component
public class DestinationMapper {

	public DestinationResponse toResponse(Destination destination) {
		return new DestinationResponse(destination.getSlug(), destination.getType(), destination.getName(),
				destination.getCountryCode(), destination.getHotelCount(), destination.getImageUrl());
	}

	public AdminDestinationResponse toAdminResponse(Destination destination) {
		return new AdminDestinationResponse(destination.getId(), destination.getSlug(), destination.getType(),
				destination.getName(), destination.getCountryCode(), destination.getHotelCount(),
				destination.getImageUrl(), destination.getFeaturedRank(), destination.getSource(),
				destination.getLastSyncedAt(), destination.getUpdatedAt());
	}

	public PopularDestinationResponse toPopular(Destination destination, List<PopularHotel> hotels) {
		return new PopularDestinationResponse(destination.getSlug(), destination.getType(), destination.getName(),
				destination.getImageUrl(), destination.getHotelCount(), hotels);
	}

	public PopularHotel toPopularHotel(HotelCardView card) {
		return new PopularHotel(card.id(), card.slug(), card.name(), card.starRating(), card.coverPhotoUrl(),
				card.userRatingAvg(), card.userRatingCount(), card.city());
	}

}
