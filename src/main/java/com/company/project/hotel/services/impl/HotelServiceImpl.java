package com.company.project.hotel.services.impl;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.hotel.dto.HotelDetail;
import com.company.project.hotel.dto.RateOption;
import com.company.project.hotel.dto.RoomList;
import com.company.project.hotel.dto.RoomOption;
import com.company.project.hotel.dto.StayRequest;
import com.company.project.hotel.mapper.HotelMapper;
import com.company.project.hotel.model.Hotel;
import com.company.project.hotel.model.ListingStatus;
import com.company.project.hotel.repository.HotelDetailView;
import com.company.project.hotel.repository.HotelPolicyRepository;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.hotel.repository.RoomBedView;
import com.company.project.hotel.repository.RoomFeatureView;
import com.company.project.hotel.repository.RoomPhotoView;
import com.company.project.hotel.repository.RoomRateView;
import com.company.project.hotel.repository.RoomTypeDetailView;
import com.company.project.hotel.repository.RoomTypeRepository;
import com.company.project.hotel.repository.StayRequirement;
import com.company.project.hotel.services.HotelService;
import com.company.project.rate.repository.CancellationRuleView;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.rate.repository.RatePlanRepository;
import com.company.project.search.config.SearchProperties;
import com.company.project.search.model.SearchCriteria;
import com.company.project.tax.repository.TaxRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotelServiceImpl implements HotelService {

	private final HotelRepository hotels;

	private final HotelPolicyRepository hotelPolicies;

	private final RoomTypeRepository roomTypes;

	private final RatePlanRepository ratePlans;

	private final TaxRuleRepository taxRules;

	private final HotelMapper hotelMapper;

	private final SearchProperties limits;

	@Override
	public HotelDetail detail(String slug) {
		HotelDetailView hotel = hotels.findDetail(slug).orElseThrow(() -> new ResourceNotFoundException("Hotel", slug));
		return hotelMapper.toDetail(hotel, hotels.findGallery(hotel.id()), hotels.findFacilities(hotel.id()),
				hotelPolicies.findByHotelIdOrderBySortOrderAscIdAsc(hotel.id()));
	}

	// The native query decides which rates can be sold, in what order and at what price; JPQL then loads the room
	// types' details and the rates' descriptions and cancellation rules, only when there is something to show.
	@Override
	public RoomList rooms(String slug, StayRequest request) {
		Hotel hotel = hotels.findBySlugAndStatusAndDeletedAtIsNull(slug, ListingStatus.ACTIVE)
			.orElseThrow(() -> new ResourceNotFoundException("Hotel", slug));
		SearchCriteria criteria = SearchCriteria.of(request.checkIn(), request.checkOut(), request.rooms(),
				request.adults(), request.childAges(), SearchCriteria.today(), limits);
		List<RoomRateView> rates = hotels.findRoomRates(hotel.getId(), new StayRequirement(criteria.checkIn(),
				criteria.checkOut(), criteria.rooms(), criteria.adultsPerRoom(), criteria.childrenPerRoom()));
		String country = hotel.getAddress().getCountryCode();
		boolean taxesIncluded = !taxRules
			.findCountriesWithTaxesOnTop(Set.of(country), criteria.checkIn(), criteria.checkOut().minusDays(1))
			.contains(country);
		return hotelMapper.toRoomList(criteria, hotel.getCurrency(), taxesIncluded,
				rates.isEmpty() ? List.of() : roomOptions(rates, criteria, hotel.getCurrency()));
	}

	private List<RoomOption> roomOptions(List<RoomRateView> rates, SearchCriteria criteria, String currency) {
		List<Long> roomTypeIds = rates.stream().map(RoomRateView::roomTypeId).distinct().toList();
		List<Long> ratePlanIds = rates.stream().map(RoomRateView::ratePlanId).toList();
		Map<Long, RoomTypeDetailView> details = roomTypes.findDetails(roomTypeIds).stream()
			.collect(Collectors.toMap(RoomTypeDetailView::id, Function.identity()));
		Map<Long, List<RoomBedView>> beds = roomTypes.findBeds(roomTypeIds).stream()
			.collect(Collectors.groupingBy(RoomBedView::roomTypeId));
		Map<Long, List<RoomFeatureView>> features = roomTypes.findFeatures(roomTypeIds).stream()
			.collect(Collectors.groupingBy(RoomFeatureView::roomTypeId));
		Map<Long, List<String>> photos = roomTypes.findPhotos(roomTypeIds).stream()
			.collect(Collectors.groupingBy(RoomPhotoView::roomTypeId,
					Collectors.mapping(RoomPhotoView::url, Collectors.toList())));
		Map<Long, RateOfferView> offers = ratePlans.findOfferDetails(ratePlanIds).stream()
			.collect(Collectors.toMap(RateOfferView::ratePlanId, Function.identity()));
		Map<Long, List<CancellationRuleView>> rules = ratePlans.findCancellationRules(ratePlanIds).stream()
			.collect(Collectors.groupingBy(CancellationRuleView::ratePlanId));
		// One "now" for every rate, so they all agree on what is still free.
		Instant now = Instant.now();
		// Rows come fitting room types first, cheapest first: a room type takes the place of its cheapest rate.
		Map<Long, List<RoomRateView>> byRoomType = rates.stream()
			.collect(Collectors.groupingBy(RoomRateView::roomTypeId, LinkedHashMap::new, Collectors.toList()));
		return byRoomType.entrySet().stream().map(entry -> {
			Long roomTypeId = entry.getKey();
			RoomRateView first = entry.getValue().getFirst();
			List<RateOption> options = entry.getValue()
				.stream()
				.map(rate -> hotelMapper.toRate(rate, offers.get(rate.ratePlanId()),
						rules.getOrDefault(rate.ratePlanId(), List.of()), criteria, currency, now))
				.toList();
			return hotelMapper.toRoomOption(details.get(roomTypeId), beds.getOrDefault(roomTypeId, List.of()),
					features.getOrDefault(roomTypeId, List.of()), photos.getOrDefault(roomTypeId, List.of()),
					first.roomsLeft(), first.fitsGuests(), options);
		}).toList();
	}

}
