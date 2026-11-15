package com.company.project.booking.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.company.project.booking.dto.BookedRoom;
import com.company.project.booking.dto.BookerDetails;
import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.dto.CheckoutQuote;
import com.company.project.booking.dto.DiscountAmount;
import com.company.project.booking.dto.PriceBreakdown;
import com.company.project.booking.dto.QuoteHotel;
import com.company.project.booking.dto.QuoteLine;
import com.company.project.booking.dto.TaxAmount;
import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingRoom;
import com.company.project.booking.model.Charges;
import com.company.project.booking.model.PricedGroup;
import com.company.project.booking.model.PricedLine;
import com.company.project.common.dto.Money;
import com.company.project.hotel.dto.RateOption;
import com.company.project.hotel.mapper.HotelMapper;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.search.model.SearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Converts a priced cart group into the checkout quote, and a new booking into its response. A line's rate is
 * described exactly as on the hotel page.
 */
@Component
@RequiredArgsConstructor
public class BookingMapper {

	private final HotelMapper hotelMapper;

	public CheckoutQuote toQuote(PricedGroup group, Charges charges, BookerDetails booker, Instant now) {
		return new CheckoutQuote(toHotel(group), group.checkIn(), group.checkOut(), group.nights(),
				group.lines().stream().map(line -> toLine(group, line, now)).toList(),
				toBreakdown(charges, group.currency()), booker);
	}

	/** {@code booking} was made from {@code group}: its rooms are the group's lines' rooms, in the same order. */
	public BookingResponse toBooking(Booking booking, PricedGroup group, Charges charges, Instant now) {
		Iterator<BookingRoom> rooms = booking.getRooms().iterator();
		List<BookedRoom> booked = new ArrayList<>();
		for (PricedLine line : group.lines()) {
			RateOption rate = toRate(group, line, now);
			for (int i = 0; i < line.line().quantity(); i++) {
				BookingRoom room = rooms.next();
				booked.add(new BookedRoom(room.getRoomTypeId(), room.getRoomName(), room.getRatePlanId(),
						line.offer().ratePlanName(), room.getBoardTypeCode(), room.getAdults(),
						room.guests().childAges(), room.getLeadGuestName(),
						Money.ofMinor(room.getTotalMinor(), booking.getCurrency()), rate.cancellation()));
			}
		}
		return new BookingResponse(booking.getReference(), booking.getStatus(), booking.getHoldExpiresAt(),
				toHotel(group), booking.getCheckIn(), booking.getCheckOut(), group.nights(),
				new BookerDetails(booking.getBookerName(), booking.getBookerEmail(), booking.getBookerPhone(),
						booking.getBookerCountry()),
				booking.getGuestName(), booking.getSpecialRequests(), booking.getEstimatedArrival(), booked,
				toBreakdown(charges, booking.getCurrency()), booking.getCreatedAt());
	}

	public PriceBreakdown toBreakdown(Charges charges, String currency) {
		DiscountAmount discount = charges.discount() == null ? null
				: new DiscountAmount(charges.discount().code(), Money.ofMinor(charges.discountMinor(), currency));
		List<TaxAmount> taxes = charges.taxes().stream()
			.map(tax -> new TaxAmount(tax.name(), tax.included(), Money.ofMinor(tax.amountMinor(), currency)))
			.toList();
		return new PriceBreakdown(Money.ofMinor(charges.subtotalMinor(), currency), discount, taxes,
				Money.ofMinor(charges.totalMinor(), currency));
	}

	private QuoteLine toLine(PricedGroup group, PricedLine priced, Instant now) {
		RateOption option = toRate(group, priced, now);
		return new QuoteLine(priced.line().itemId(), priced.line().ratePlanId(), priced.offer().ratePlanName(),
				priced.line().roomTypeId(), priced.offer().roomTypeName(), priced.offer().boardCode(),
				priced.offer().boardName(), priced.line().quantity(), priced.line().guests().adults(),
				priced.line().guests().childAges(), option.totalPrice(),
				Money.ofMinor(priced.subtotalMinor(), group.currency()), option.cancellation());
	}

	private static QuoteHotel toHotel(PricedGroup group) {
		HotelCardView hotel = group.hotel();
		return new QuoteHotel(hotel.id(), hotel.slug(), hotel.name(), hotel.city(), hotel.countryCode(),
				group.lines().getFirst().offer().checkInFrom());
	}

	// The rate's price per room and cancellation terms, as the hotel page's room list shows them.
	private RateOption toRate(PricedGroup group, PricedLine priced, Instant now) {
		SearchCriteria stay = new SearchCriteria(group.checkIn(), group.checkOut(), 1, priced.line().guests().adults(),
				priced.line().guests().childAges());
		return hotelMapper.toRate(priced.rate(), priced.offer(), priced.rules(), stay, group.currency(), now);
	}

}
