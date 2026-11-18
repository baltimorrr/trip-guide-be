package com.company.project.payment.refund;

import com.company.project.payment.services.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resends to Stripe the refunds that didn't get there after their cancellation (C-PAY-05), on
 * {@code app.payment.refund-retry-cron}, one batch of 100 per tick. A failed run is logged and retried at the next.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RefundRetryJob {

	private final RefundService refunds;

	@Scheduled(cron = "${app.payment.refund-retry-cron}")
	void retryUnsentRefunds() {
		try {
			int handled = refunds.retryUnsent();
			if (handled > 0) {
				log.info("Retried {} refunds that hadn't reached Stripe", handled);
			}
		}
		catch (RuntimeException ex) {
			log.error("Retrying unsent refunds failed", ex);
		}
	}

}
