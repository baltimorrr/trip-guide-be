package com.company.project.destination.dto;

import java.time.Instant;

import com.company.project.destination.model.DestinationSource;
import com.company.project.destination.model.DestinationType;

/** A destination as admins see it: sync data plus the fields they own (name, image, featured rank). */
public record AdminDestinationResponse(Long id, String slug, DestinationType type, String name, String countryCode,
		int hotelCount, String imageUrl, Short featuredRank, DestinationSource source, Instant lastSyncedAt,
		Instant updatedAt) {
}
