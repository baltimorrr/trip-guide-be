package com.company.project.cart.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.company.project.cart.dto.CartGroup;
import com.company.project.cart.dto.CartHotel;
import com.company.project.cart.dto.CartItemResponse;
import com.company.project.cart.dto.CartItemStatus;
import com.company.project.cart.repository.CartLineView;
import com.company.project.common.dto.Money;
import com.company.project.hotel.dto.RateOption;
import com.company.project.hotel.mapper.HotelMapper;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.RoomRateView;
import com.company.project.rate.repository.CancellationRuleView;
import com.company.project.rate.repository.RateOfferView;
import com.company.project.search.model.SearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts priced cart lines into the cart response. A line's rate is described exactly as on the hotel page. */
@Component
@RequiredArgsConstructor
public class CartMapper {

	private final HotelMapper hotelMapper;

	/**
	 * One line. {@code rate} is the room list's row for the line's rate plan and stay, null when there is none; it
	 * is read only when the status says the line is priced.
	 */
	public CartItemResponse toItem(CartLineView line, CartItemStatus status, RoomRateView rate, RateOfferView offer,
			List<CancellationRuleView> rules, String photoUrl, Instant now) {
		Money priceAtAdd = Money.ofMinor(line.priceAtAddMinor(), line.currency());
		if (!isPriced(status)) {
			return new CartItemResponse(line.itemId(), line.ratePlanId(), offer.ratePlanName(), line.roomTypeId(),
					offer.roomTypeName(), photoUrl, offer.boardCode(), offer.boardName(), line.quantity(),
					line.guests().adults(), line.guests().childAges(), status, null, null, null, null, priceAtAdd,
					false);
		}
		// The rate's cancellation terms and price per room, as the hotel page's room list shows them.
		SearchCriteria stay = new SearchCriteria(line.checkIn(), line.checkOut(), 1, line.guests().adults(),
				line.guests().childAges());
		RateOption option = hotelMapper.toRate(rate, offer, rules, stay, line.hotelCurrency(), now);
		boolean priceChanged = rate.stayPriceMinor() != line.priceAtAddMinor()
				|| !line.hotelCurrency().equals(line.currency());
		return new CartItemResponse(line.itemId(), line.ratePlanId(), offer.ratePlanName(), line.roomTypeId(),
				offer.roomTypeName(), photoUrl, offer.boardCode(), offer.boardName(), line.quantity(),
				line.guests().adults(), line.guests().childAges(), status, rate.roomsLeft(), option.cancellation(),
				option.totalPrice(), Money.ofMinor(rate.stayPriceMinor() * line.quantity(), line.hotelCurrency()),
				priceAtAdd, priceChanged);
	}

	/** One hotel and date range, with its lines in the order they were added. */
	public CartGroup toGroup(HotelCardView hotel, boolean listed, LocalDate checkIn, LocalDate checkOut,
			boolean taxesIncluded, List<CartItemResponse> items) {
		List<Money> available = items.stream()
			.filter(item -> item.status() == CartItemStatus.AVAILABLE)
			.map(CartItemResponse::totalPrice)
			.toList();
		// Every available line sells in the hotel's current currency, so their prices add up.
		Money total = available.isEmpty() ? null
				: new Money(available.stream().map(Money::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
						available.getFirst().currency());
		return new CartGroup(
				new CartHotel(hotel.id(), hotel.slug(), hotel.name(), hotel.starRating(), hotel.coverPhotoUrl(),
						hotel.city(), hotel.countryCode(), listed),
				checkIn, checkOut, (int) ChronoUnit.DAYS.between(checkIn, checkOut), taxesIncluded,
				available.size() == items.size(), total, items);
	}

	private static boolean isPriced(CartItemStatus status) {
		return status == CartItemStatus.AVAILABLE || status == CartItemStatus.NOT_ENOUGH_ROOMS;
	}

}
