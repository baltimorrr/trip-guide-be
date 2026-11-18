package com.company.project.booking.services.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.dto.CancelBookingRequest;
import com.company.project.booking.dto.CancellationQuote;
import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingCancelled;
import com.company.project.booking.model.BookingNight;
import com.company.project.booking.model.BookingRoom;
import com.company.project.booking.model.CancellationCharges;
import com.company.project.booking.model.Payment;
import com.company.project.booking.model.PaymentStatus;
import com.company.project.booking.model.Refund;
import com.company.project.booking.repository.BookingRepository;
import com.company.project.booking.repository.PaymentRepository;
import com.company.project.booking.repository.RefundRepository;
import com.company.project.booking.repository.RoomInventoryRepository;
import com.company.project.booking.services.BookingCancellationService;
import com.company.project.common.dto.Money;
import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.hotel.repository.HotelContactView;
import com.company.project.hotel.repository.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class BookingCancellationServiceImpl implements BookingCancellationService {

	private static final String REASON = "Cancelled by customer";

	private final BookingRepository bookings;

	private final PaymentRepository payments;

	private final RefundRepository refunds;

	private final RoomInventoryRepository roomInventory;

	private final HotelRepository hotels;

	private final BookingResponses responses;

	private final ApplicationEventPublisher events;

	@Override
	public CancellationQuote quote(long userId, String reference) {
		Booking booking = bookings.findByReferenceAndUserId(reference, userId)
			.orElseThrow(() -> new ResourceNotFoundException("Booking", reference));
		checkCancellable(booking);
		Instant now = Instant.now();
		CancellationCharges charges = charges(booking, paid(booking), now);
		String currency = booking.getCurrency();
		return new CancellationQuote(reference, Money.ofMinor(charges.paidMinor(), currency),
				Money.ofMinor(charges.penaltyMinor(), currency), Money.ofMinor(charges.refundMinor(), currency), now);
	}

	// Locks in the order every write to an existing booking uses: its row, then its room inventory. The refund goes
	// to Stripe only after this commits (RefundSendListener), so a rolled-back cancellation never refunds.
	@Override
	@Transactional
	public BookingResponse cancel(long userId, String reference, CancelBookingRequest request) {
		Booking booking = bookings.findByReferenceAndUserIdForUpdate(reference, userId)
			.orElseThrow(() -> new ResourceNotFoundException("Booking", reference));
		checkCancellable(booking);
		Instant now = Instant.now();
		Payment payment = paid(booking);
		CancellationCharges charges = charges(booking, payment, now);
		Money refund = Money.ofMinor(charges.refundMinor(), booking.getCurrency());
		if (!refund.currency().equals(request.expectedRefund().currency())
				|| refund.amount().compareTo(request.expectedRefund().amount()) != 0) {
			throw new BusinessException(HttpStatus.CONFLICT, "The refund is now %s %s. Check it and confirm"
				.formatted(refund.amount().toPlainString(), refund.currency()));
		}
		String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
		// Before any change: the status log reads both when the change is flushed.
		bookings.setChangedBy(userId);
		bookings.setStatusReason(reason == null ? REASON : REASON + ": " + reason);
		// Before the rooms turn CANCELLED: release gives back the nights of the booking's active rooms.
		roomInventory.release(List.of(booking.getId()));
		booking.cancel(now, userId, reason, charges.penaltyMinor());
		Long refundId = null;
		if (charges.refundMinor() > 0) {
			refundId = refunds.saveAndFlush(Refund.create(payment, charges.refundMinor(), REASON, userId)).getId();
		}
		bookings.flush();
		events.publishEvent(new BookingCancelled(booking.getId(), refundId));
		return responses.of(booking, now);
	}

	private CancellationCharges charges(Booking booking, Payment payment, Instant now) {
		HotelContactView hotel = hotels.findContact(booking.getHotelId()).orElseThrow();
		List<CancellationCharges.Room> rooms = booking.getRooms()
			.stream()
			.filter(BookingRoom::isActive)
			.map(room -> new CancellationCharges.Room(room.getCancellationPolicy(),
					room.getNights().stream().map(BookingNight::getPriceMinor).toList(), room.getTotalMinor()))
			.toList();
		return CancellationCharges.of(rooms, booking.getCheckIn(), hotel.checkInFrom(), ZoneId.of(hotel.timezone()),
				booking.getCurrency(), booking.getSubtotalMinor(), payment == null ? 0 : payment.getAmountMinor(), now);
	}

	// Null when there was nothing to pay (a 100% code).
	private Payment paid(Booking booking) {
		return payments.findFirstByBookingIdAndStatusIn(booking.getId(), Set.of(PaymentStatus.SUCCEEDED)).orElse(null);
	}

	private static void checkCancellable(Booking booking) {
		switch (booking.getStatus()) {
			case CONFIRMED -> {
			}
			case PENDING -> throw new BusinessException(HttpStatus.CONFLICT,
					"An unpaid booking can't be cancelled. Its rooms are released when the hold ends");
			case CANCELLED, EXPIRED -> throw new BusinessException(HttpStatus.CONFLICT,
					"This booking is already " + booking.getStatus().name().toLowerCase(Locale.ROOT));
			default -> throw new BusinessException(HttpStatus.CONFLICT, "This booking can no longer be cancelled");
		}
	}

}
