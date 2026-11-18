package com.company.project.booking.mapper;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.company.project.booking.dto.BookedRoom;
import com.company.project.booking.dto.BookerDetails;
import com.company.project.booking.dto.BookingCancellation;
import com.company.project.booking.dto.BookingHotel;
import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.dto.CheckoutQuote;
import com.company.project.booking.dto.DiscountAmount;
import com.company.project.booking.dto.PaymentDetails;
import com.company.project.booking.dto.PriceBreakdown;
import com.company.project.booking.dto.QuoteHotel;
import com.company.project.booking.dto.QuoteLine;
import com.company.project.booking.dto.RefundDetails;
import com.company.project.booking.dto.TaxAmount;
import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingNight;
import com.company.project.booking.model.BookingRoom;
import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.model.CancellationTerms;
import com.company.project.booking.model.Charges;
import com.company.project.booking.model.Payment;
import com.company.project.booking.model.PricedGroup;
import com.company.project.booking.model.PricedLine;
import com.company.project.booking.model.Refund;
import com.company.project.common.dto.Money;
import com.company.project.hotel.dto.RateCancellation;
import com.company.project.hotel.dto.RateOption;
import com.company.project.hotel.mapper.HotelMapper;
import com.company.project.hotel.repository.HotelCardView;
import com.company.project.hotel.repository.HotelContactView;
import com.company.project.search.model.CancellationSchedule;
import com.company.project.search.model.SearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Converts a priced cart group into the checkout quote, and a stored booking into its response. A line's rate is
 * described exactly as on the hotel page; a booked room's terms come from the policy and night prices it was sold
 * with.
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

	/**
	 * The booking as it is now, from what it stored: the rooms in booking order with their cancellation terms at
	 * {@code now}, the breakdown from its totals and tax rows, its payment and, once cancelled, its refund.
	 *
	 * @param ratePlanNames the rooms' rate plans' names by id
	 * @param discountCode the code applied, or null
	 * @param payment the latest payment, or null
	 * @param refund the refund of its cancellation, or null
	 */
	public BookingResponse toBooking(Booking booking, HotelContactView hotel, Map<Long, String> ratePlanNames,
			String discountCode, Payment payment, Refund refund, Instant now) {
		String currency = booking.getCurrency();
		List<BookedRoom> rooms = booking.getRooms()
			.stream()
			.map(room -> new BookedRoom(room.getRoomTypeId(), room.getRoomName(), room.getRatePlanId(),
					ratePlanNames.get(room.getRatePlanId()), room.getBoardTypeCode(), room.getAdults(),
					room.guests().childAges(), room.getLeadGuestName(), Money.ofMinor(room.getTotalMinor(), currency),
					toCancellation(booking, room, hotel, now)))
			.toList();
		DiscountAmount discount = booking.getDiscountCodeId() == null ? null
				: new DiscountAmount(discountCode, Money.ofMinor(booking.getDiscountMinor(), currency));
		List<TaxAmount> taxes = booking.getTaxes()
			.stream()
			.map(tax -> new TaxAmount(tax.getName(), tax.isIncluded(), Money.ofMinor(tax.getAmountMinor(), currency)))
			.toList();
		PriceBreakdown price = new PriceBreakdown(Money.ofMinor(booking.getSubtotalMinor(), currency), discount, taxes,
				Money.ofMinor(booking.getTotalMinor(), currency));
		return new BookingResponse(booking.getReference(), booking.getStatus(),
				booking.getStatus() == BookingStatus.PENDING ? booking.getHoldExpiresAt() : null,
				booking.getConfirmedAt(), toHotel(hotel), booking.getCheckIn(), booking.getCheckOut(),
				booking.getCheckIn().until(booking.getCheckOut()).getDays(),
				new BookerDetails(booking.getBookerName(), booking.getBookerEmail(), booking.getBookerPhone(),
						booking.getBookerCountry()),
				booking.getGuestName(), booking.getSpecialRequests(), booking.getEstimatedArrival(), rooms, price,
				toPaymentDetails(payment), toCancellation(booking, refund), booking.getCreatedAt());
	}

	/** Null for none. */
	public PaymentDetails toPaymentDetails(Payment payment) {
		return payment == null ? null
				: new PaymentDetails(payment.getStatus(), payment.getFailureReason(), payment.getPaidAt());
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

	private BookingHotel toHotel(HotelContactView hotel) {
		return new BookingHotel(hotel.id(), hotel.slug(), hotel.name(), hotel.city(), hotel.countryCode(),
				hotelMapper.toAddress(hotel), hotel.phone(), hotel.email(), hotel.checkInFrom(), hotel.checkOutUntil());
	}

	// The rate's price per room and cancellation terms, as the hotel page's room list shows them.
	private RateOption toRate(PricedGroup group, PricedLine priced, Instant now) {
		SearchCriteria stay = new SearchCriteria(group.checkIn(), group.checkOut(), 1, priced.line().guests().adults(),
				priced.line().guests().childAges());
		return hotelMapper.toRate(priced.rate(), priced.offer(), priced.rules(), stay, group.currency(), now);
	}

	// The terms the room was sold with, counted from the hotel's check-in time in its own zone, as on the hotel page.
	private RateCancellation toCancellation(Booking booking, BookingRoom room, HotelContactView hotel, Instant now) {
		CancellationTerms terms = room.getCancellationPolicy();
		List<CancellationSchedule.Rule> rules = terms.rules()
			.stream()
			.map(rule -> new CancellationSchedule.Rule(rule.hoursBeforeCheckIn(), rule.penaltyType(), rule.penaltyValue()))
			.toList();
		Integer penaltyFromHours = rules.stream()
			.filter(rule -> rule.value().signum() > 0)
			.map(CancellationSchedule.Rule::hoursBeforeCheckIn)
			.max(Comparator.naturalOrder())
			.orElse(null);
		return hotelMapper.toCancellation(booking.getCheckIn(), hotel.checkInFrom(), ZoneId.of(hotel.timezone()),
				terms.refundable(), terms.name(), penaltyFromHours, rules,
				room.getNights().stream().map(BookingNight::getPriceMinor).toList(), booking.getCurrency(), now);
	}

	private static BookingCancellation toCancellation(Booking booking, Refund refund) {
		if (booking.getStatus() != BookingStatus.CANCELLED) {
			return null;
		}
		String currency = booking.getCurrency();
		long penalty = booking.getCancellationFeeMinor() == null ? 0 : booking.getCancellationFeeMinor();
		return new BookingCancellation(booking.getCancelledAt(), booking.getCancelReason(),
				Money.ofMinor(penalty, currency),
				refund == null ? null : new RefundDetails(Money.ofMinor(refund.getAmountMinor(), currency), refund.getStatus()));
	}

}
