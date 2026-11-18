package com.company.project.booking.notification;

import com.company.project.booking.dto.BookingNotice;
import com.company.project.booking.model.BookingCancelled;
import com.company.project.booking.model.BookingConfirmed;
import com.company.project.booking.services.BookingMailService;
import com.company.project.booking.services.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends a booking's emails once the transaction that changed it has committed, so no email announces a change that
 * rolled back. A failed email is logged and never fails the change, which is already saved (a crash in between loses
 * the email: accepted while emails are only logged).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BookingMailListener {

	private final BookingService bookingService;

	private final BookingMailService mails;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onConfirmed(BookingConfirmed event) {
		BookingNotice notice = notice(event.bookingId(), "confirmation");
		if (notice == null) {
			return;
		}
		send("confirmation", notice, () -> mails.sendConfirmation(notice.booking().booker().email(), notice.booking()));
		if (hasOwner(notice)) {
			send("new-booking notice", notice,
					() -> mails.notifyOwnerOfNewBooking(notice.ownerEmail(), notice.booking()));
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onCancelled(BookingCancelled event) {
		BookingNotice notice = notice(event.bookingId(), "cancellation");
		if (notice == null) {
			return;
		}
		send("cancellation", notice, () -> mails.sendCancellation(notice.booking().booker().email(), notice.booking()));
		if (hasOwner(notice)) {
			send("cancellation notice", notice,
					() -> mails.notifyOwnerOfCancellation(notice.ownerEmail(), notice.booking()));
		}
	}

	private BookingNotice notice(long bookingId, String what) {
		try {
			return bookingService.notice(bookingId);
		}
		catch (RuntimeException ex) {
			log.error("Couldn't read booking {} for its {} emails", bookingId, what, ex);
			return null;
		}
	}

	private static boolean hasOwner(BookingNotice notice) {
		if (notice.ownerEmail() == null) {
			log.warn("Booking {}: the hotel has no partner contact email, so it isn't told", notice.booking().reference());
			return false;
		}
		return true;
	}

	private static void send(String what, BookingNotice notice, Runnable mail) {
		try {
			mail.run();
		}
		catch (RuntimeException ex) {
			log.error("Couldn't send the {} of booking {}", what, notice.booking().reference(), ex);
		}
	}

}
