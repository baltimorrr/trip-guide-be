package com.company.project.destination.services.impl;

import java.util.List;

import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.destination.dto.AdminDestinationResponse;
import com.company.project.destination.dto.DestinationResponse;
import com.company.project.destination.dto.DestinationUpdateRequest;
import com.company.project.destination.dto.PopularDestinationResponse;
import com.company.project.destination.mapper.DestinationMapper;
import com.company.project.destination.model.Destination;
import com.company.project.destination.repository.DestinationRepository;
import com.company.project.destination.services.DestinationService;
import com.company.project.hotel.repository.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only transactions by default; methods that write opt in with {@code @Transactional}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationServiceImpl implements DestinationService {

	static final int MAX_POPULAR = 20;

	static final int MAX_HOTELS_PER_DESTINATION = 10;

	private final DestinationRepository destinations;

	private final HotelRepository hotels;

	private final DestinationMapper destinationMapper;

	@Override
	public List<PopularDestinationResponse> popular(int limit, int hotelsPerDestination) {
		int size = Math.clamp(limit, 1, MAX_POPULAR);
		List<Destination> popular = destinations.findFeatured(size);
		if (popular.isEmpty()) {
			popular = destinations.findTopCities(size);
		}
		// One small indexed query per destination (at most MAX_POPULAR): a per-group "top N" in one JPQL
		// statement would need a lateral join or a window function.
		int perDestination = Math.clamp(hotelsPerDestination, 1, MAX_HOTELS_PER_DESTINATION);
		return popular.stream()
			.map(destination -> destinationMapper.toPopular(destination,
					hotels.findTopInDestination(destination.getId(), perDestination)
						.stream()
						.map(destinationMapper::toPopularHotel)
						.toList()))
			.toList();
	}

	@Override
	public DestinationResponse findBySlug(String slug) {
		return destinations.findBySlugAndDeletedAtIsNull(slug)
			.map(destinationMapper::toResponse)
			.orElseThrow(() -> new ResourceNotFoundException("Destination", slug));
	}

	@Override
	public Page<AdminDestinationResponse> adminSearch(String namePrefix, Pageable pageable) {
		return destinations.searchByNamePrefix(namePrefix.trim(), pageable).map(destinationMapper::toAdminResponse);
	}

	@Override
	@Transactional
	public AdminDestinationResponse update(Long id, DestinationUpdateRequest request) {
		Destination destination = destinations.findById(id)
			.filter(found -> found.getDeletedAt() == null)
			.orElseThrow(() -> new ResourceNotFoundException("Destination", id));
		destination.updateDisplay(request.name(), request.imageUrl(), request.featuredRank());
		// Flush now so the new version and updatedAt are in the response.
		return destinationMapper.toAdminResponse(destinations.saveAndFlush(destination));
	}

}
