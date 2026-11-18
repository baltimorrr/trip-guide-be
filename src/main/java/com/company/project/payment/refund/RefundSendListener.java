package com.company.project.payment.refund;

import com.company.project.booking.model.BookingCancelled;
import com.company.project.payment.services.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends a cancellation's refund to Stripe once the cancellation has committed, so a cancellation that rolled back
 * never refunds. A refund that doesn't reach Stripe here is resent by {@link RefundRetryJob}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RefundSendListener {

	private final RefundService refunds;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onCancelled(BookingCancelled event) {
		if (event.refundId() == null) {
			return;
		}
		try {
			refunds.send(event.refundId());
		}
		catch (RuntimeException ex) {
			log.error("Sending refund {} of booking {} failed; it is sent again within minutes", event.refundId(),
					event.bookingId(), ex);
		}
	}

}
