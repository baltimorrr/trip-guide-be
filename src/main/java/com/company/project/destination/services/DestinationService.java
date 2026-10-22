package com.company.project.destination.services;

import java.util.List;

import com.company.project.destination.dto.AdminDestinationResponse;
import com.company.project.destination.dto.DestinationResponse;
import com.company.project.destination.dto.DestinationUpdateRequest;
import com.company.project.destination.dto.PopularDestinationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Reading destinations, and the admin edits on top of the sync. Missing or deleted ids and slugs are 404. */
public interface DestinationService {

	/**
	 * Featured destinations in admin order, or the cities with the most hotels while none is featured,
	 * each with its best-rated listed hotels.
	 */
	List<PopularDestinationResponse> popular(int limit, int hotelsPerDestination);

	DestinationResponse findBySlug(String slug);

	Page<AdminDestinationResponse> adminSearch(String namePrefix, Pageable pageable);

	AdminDestinationResponse update(Long id, DestinationUpdateRequest request);

}
