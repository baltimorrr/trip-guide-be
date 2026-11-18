package com.company.project.booking.services.impl;

import java.util.stream.Collectors;

import com.company.project.booking.dto.BookedRoom;
import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.services.BookingMailService;
import com.company.project.common.dto.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * No email service yet: each email is written to the log, like the account emails. The recipient and reference are
 * logged at INFO; the body holds guests' names and contact details, so it is logged at DEBUG only.
 */
@Slf4j
@Service
class BookingMailServiceImpl implements BookingMailService {

	BookingMailServiceImpl() {
		log.warn("No email service: booking confirmations and cancellations are written to this log");
	}

	@Override
	public void sendConfirmation(String email, BookingResponse booking) {
		write("Booking confirmation", email, booking);
	}

	@Override
	public void notifyOwnerOfNewBooking(String email, BookingResponse booking) {
		write("New booking", email, booking);
	}

	@Override
	public void sendCancellation(String email, BookingResponse booking) {
		write("Booking cancelled", email, booking);
	}

	@Override
	public void notifyOwnerOfCancellation(String email, BookingResponse booking) {
		write("Booking cancelled by the guest", email, booking);
	}

	private static void write(String subject, String email, BookingResponse booking) {
		log.info("{} {} to {}", subject, booking.reference(), email);
		if (log.isDebugEnabled()) {
			log.debug("{} {}:\n{}", subject, booking.reference(), body(booking));
		}
	}

	private static String body(BookingResponse booking) {
		StringBuilder text = new StringBuilder();
		text.append("Booking ").append(booking.reference()).append(" (").append(booking.status()).append(")\n")
			.append(booking.hotel().name()).append(", ").append(booking.hotel().address().formatted()).append('\n')
			.append("Contact: ").append(booking.hotel().phone()).append(" ").append(booking.hotel().email()).append('\n')
			.append(booking.checkIn()).append(" to ").append(booking.checkOut()).append(", ").append(booking.nights())
			.append(" nights; check-in from ").append(booking.hotel().checkInFrom()).append(", check-out until ")
			.append(booking.hotel().checkOutUntil()).append('\n')
			.append("Booked by ").append(booking.booker().fullName());
		if (booking.guestName() != null) {
			text.append(" for ").append(booking.guestName());
		}
		text.append('\n');
		for (BookedRoom room : booking.rooms()) {
			text.append("- ").append(room.roomName()).append(", ").append(room.ratePlanName()).append(", ")
				.append(room.boardType()).append(", ").append(room.adults()).append(" adults")
				.append(room.childAges().isEmpty() ? "" : ", children aged " + room.childAges())
				.append(room.leadGuestName() == null ? "" : ", " + room.leadGuestName()).append(": ")
				.append(money(room.price())).append("; ")
				.append(room.cancellation().refundable() ? room.cancellation().policyName() : "Non-refundable")
				.append(room.cancellation().freeUntil() == null ? ""
						: ", free cancellation until " + room.cancellation().freeUntil())
				.append('\n');
		}
		text.append("Subtotal ").append(money(booking.price().subtotal())).append('\n');
		if (booking.price().discount() != null) {
			text.append("Discount ").append(booking.price().discount().code()).append(" -")
				.append(money(booking.price().discount().amount())).append('\n');
		}
		text.append(booking.price().taxes().stream()
			.map(tax -> tax.name() + (tax.included() ? " (included) " : " ") + money(tax.amount()))
			.collect(Collectors.joining("\n", "", booking.price().taxes().isEmpty() ? "" : "\n")));
		text.append("Total ").append(money(booking.price().total()));
		if (booking.cancellation() != null) {
			text.append("\nCancelled ").append(booking.cancellation().cancelledAt()).append(", penalty ")
				.append(money(booking.cancellation().penalty())).append(", refund ")
				.append(booking.cancellation().refund() == null ? "none"
						: money(booking.cancellation().refund().amount()) + " ("
								+ booking.cancellation().refund().status() + ")");
		}
		return text.toString();
	}

	private static String money(Money money) {
		return money.amount().toPlainString() + " " + money.currency();
	}

}
