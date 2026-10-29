package com.company.project.hotel.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.company.project.common.dto.Money;
import com.company.project.hotel.dto.CancellationPenalty;
import com.company.project.hotel.dto.Facility;
import com.company.project.hotel.dto.FacilityGroup;
import com.company.project.hotel.dto.GalleryPhoto;
import com.company.project.hotel.dto.HotelAddress;
import com.company.project.hotel.dto.HotelDetail;
import com.company.project.hotel.dto.HotelPolicies;
import com.company.project.hotel.dto.HotelPolicyItem;
import com.company.project.hotel.dto.RateCancellation;
import com.company.project.hotel.dto.RateOption;
import com.company.project.hotel.dto.RoomBed;
import com.company.project.hotel.dto.RoomFeature;
import com.company.project.hotel.dto.RoomList;
import com.company.project.hotel.dto.RoomOption;
import com.company.project.hotel.model.HotelPolicy;
import com.company.project.hotel.repository.GalleryPhotoView;
import com.company.project.hotel.repository.HotelDetailView;
import com.company.project.hotel.repository.HotelFacilityView;
import com.company.project.hotel.repository.RoomBedView;
import com.company.project.hotel.repository.RoomFeatureView;
import com.company.project.hotel.repository.RoomRateView;
import com.company.project.hotel.repository.RoomTypeDetailView;
import com.company.project.rate.repository.CancellationRuleView;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.search.model.CancellationSchedule;
import com.company.project.search.model.FreeCancellation;
import com.company.project.search.model.SearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Converts hotel page projections into responses: the one-line address, the room list and its cancellation terms. */
@Component
public class HotelMapper {

	public HotelDetail toDetail(HotelDetailView hotel, List<GalleryPhotoView> photos,
			List<HotelFacilityView> facilities, List<HotelPolicy> policies) {
		HotelAddress address = new HotelAddress(hotel.line1(), hotel.ward(), hotel.district(), hotel.city(),
				hotel.countryCode(), hotel.countryName(), formatAddress(hotel), hotel.latitude(), hotel.longitude());
		return new HotelDetail(hotel.id(), hotel.slug(), hotel.name(), hotel.accommodationType(),
				hotel.accommodationTypeName(), hotel.starRating(), hotel.description(), address,
				hotel.distanceToCenterM(), hotel.googleRating(), hotel.googleRatingCount(), hotel.userRatingAvg(),
				hotel.userRatingCount(),
				photos.stream().map(p -> new GalleryPhoto(p.url(), p.category(), p.caption(), p.roomTypeId())).toList(),
				facilityGroups(facilities),
				new HotelPolicies(hotel.checkInFrom(), hotel.checkInUntil(), hotel.checkOutUntil(), hotel.minCheckInAge(),
						policies.stream().map(p -> new HotelPolicyItem(p.getType(), p.getDescription())).toList()));
	}

	public RoomList toRoomList(SearchCriteria criteria, String currency, boolean taxesIncluded,
			List<RoomOption> roomTypes) {
		return new RoomList(criteria.checkIn(), criteria.checkOut(), criteria.nights(), criteria.rooms(),
				criteria.adults(), criteria.children(), currency, taxesIncluded, roomTypes);
	}

	public RoomOption toRoomOption(RoomTypeDetailView roomType, List<RoomBedView> beds, List<RoomFeatureView> features,
			List<String> photoUrls, int roomsLeft, boolean fitsGuests, List<RateOption> rates) {
		return new RoomOption(roomType.id(), roomType.name(), roomType.description(), roomType.sizeSqm(),
				roomType.maxAdults(), roomType.maxChildren(), roomType.maxOccupancy(),
				beds.stream().map(b -> new RoomBed(b.code(), b.name(), b.quantity())).toList(),
				features.stream().map(f -> new RoomFeature(f.code(), f.name(), f.icon())).toList(), photoUrls,
				roomsLeft, fitsGuests, rates);
	}

	/**
	 * One rate of the room list, for one room, with its cancellation terms: the free deadline from
	 * {@link FreeCancellation#until} (as on the search's cards) and the penalties after it. {@code now} decides what
	 * is still to come.
	 */
	public RateOption toRate(RoomRateView rate, RateOfferView offer, List<CancellationRuleView> rules,
			SearchCriteria criteria, String currency, Instant now) {
		ZoneId zone = ZoneId.of(offer.hotelTimezone());
		OffsetDateTime freeUntil = FreeCancellation.until(criteria.checkIn(), offer.checkInFrom(), zone,
				offer.refundable(), offer.penaltyFromHours(), now);
		List<CancellationPenalty> penalties = CancellationSchedule
			.tiers(criteria.checkIn(), offer.checkInFrom(), zone,
					rules.stream()
						.map(r -> new CancellationSchedule.Rule(r.hoursBeforeCheckIn(), r.penaltyType(), r.penaltyValue()))
						.toList(),
					rate.nightPricesMinor(), currency, now)
			.stream()
			.map(tier -> new CancellationPenalty(tier.from(), tier.until(), Money.ofMinor(tier.penaltyMinor(), currency)))
			.toList();
		long perNight = BigDecimal.valueOf(rate.stayPriceMinor())
			.divide(BigDecimal.valueOf(criteria.nights()), 0, RoundingMode.HALF_UP)
			.longValueExact();
		return new RateOption(rate.ratePlanId(), offer.ratePlanName(), offer.boardCode(), offer.boardName(),
				new RateCancellation(offer.refundable(), offer.policyName(), freeUntil, penalties),
				Money.ofMinor(perNight, currency), Money.ofMinor(rate.stayPriceMinor(), currency));
	}

	// Street to country, the parts that exist; the province is left out. The country code stands in for a country
	// the destination sync has not named yet.
	static String formatAddress(HotelDetailView hotel) {
		String country = StringUtils.hasText(hotel.countryName()) ? hotel.countryName() : hotel.countryCode();
		return Stream.of(hotel.line1(), hotel.ward(), hotel.district(), hotel.city(), country)
			.filter(StringUtils::hasText)
			.map(String::trim)
			.collect(Collectors.joining(", "));
	}

	// The facilities come sorted by group, then name; keep both orders.
	private static List<FacilityGroup> facilityGroups(List<HotelFacilityView> facilities) {
		Map<String, List<Facility>> groups = new LinkedHashMap<>();
		for (HotelFacilityView f : facilities) {
			groups.computeIfAbsent(f.groupName(), name -> new ArrayList<>())
				.add(new Facility(f.code(), f.name(), f.icon(), f.free()));
		}
		return groups.entrySet()
			.stream()
			.map(group -> new FacilityGroup(group.getKey(), List.copyOf(group.getValue())))
			.toList();
	}

}
