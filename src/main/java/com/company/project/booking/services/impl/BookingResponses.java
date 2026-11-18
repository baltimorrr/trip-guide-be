package com.company.project.booking.services.impl;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.mapper.BookingMapper;
import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingRoom;
import com.company.project.booking.repository.PaymentRepository;
import com.company.project.booking.repository.RefundRepository;
import com.company.project.discount.model.DiscountCode;
import com.company.project.discount.repository.DiscountCodeRepository;
import com.company.project.hotel.repository.HotelRepository;
import com.company.project.rate.model.RatePlan;
import com.company.project.rate.repository.RatePlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds a stored booking's response with everything it shows besides its own rows: the hotel's address and contact,
 * the rate plans' names, the discount code, the latest payment and the refund. Call it inside a transaction (the
 * booking's rooms and taxes are lazy).
 */
@Component
@RequiredArgsConstructor
class BookingResponses {

	private final HotelRepository hotels;

	private final RatePlanRepository ratePlans;

	private final DiscountCodeRepository discountCodes;

	private final PaymentRepository payments;

	private final RefundRepository refunds;

	private final BookingMapper bookingMapper;

	BookingResponse of(Booking booking, Instant now) {
		Map<Long, String> ratePlanNames = ratePlans
			.findAllById(booking.getRooms().stream().map(BookingRoom::getRatePlanId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(RatePlan::getId, RatePlan::getName));
		String code = booking.getDiscountCodeId() == null ? null
				: discountCodes.findById(booking.getDiscountCodeId()).map(DiscountCode::getCode).orElse(null);
		return bookingMapper.toBooking(booking, hotels.findContact(booking.getHotelId()).orElseThrow(), ratePlanNames,
				code, payments.findFirstByBookingIdOrderByIdDesc(booking.getId()).orElse(null),
				refunds.findFirstByBookingIdOrderByIdDesc(booking.getId()).orElse(null), now);
	}

}
