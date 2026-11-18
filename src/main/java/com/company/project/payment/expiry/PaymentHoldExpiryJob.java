package com.company.project.payment.expiry;

import com.company.project.payment.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cancels on Stripe the open payments of Pending bookings whose hold has passed (C-PAY-02), on the booking expiry's
 * cron. The booking expiry skips bookings that still have an open payment, so it releases their rooms on a later tick,
 * once Stripe can no longer charge them; the two jobs need no order between them. One batch of 100 per tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PaymentHoldExpiryJob {

	private final PaymentService paymentService;

	@Scheduled(cron = "${app.booking.expiry-cron}")
	void cancelPaymentsOfExpiredHolds() {
		try {
			int settled = paymentService.cancelForExpiredHolds();
			if (settled > 0) {
				log.info("Settled {} payments of bookings whose hold ended", settled);
			}
		}
		catch (RuntimeException ex) {
			log.error("Cancelling the payments of expired holds failed", ex);
		}
	}

}
