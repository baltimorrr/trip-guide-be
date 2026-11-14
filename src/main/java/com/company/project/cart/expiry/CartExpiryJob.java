package com.company.project.cart.expiry;

import com.company.project.cart.services.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes cart lines whose check-in has passed (C-CART-04), on {@code app.cart.expiry-cron}. Until a run deletes
 * them, reading the cart already marks such lines {@code CHECK_IN_PASSED}, so the job doesn't run on startup. A
 * failed run is logged and retried at the next tick; running on several instances at once is harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CartExpiryJob {

	private final CartService cartService;

	@Scheduled(cron = "${app.cart.expiry-cron}")
	void removeExpired() {
		try {
			int removed = cartService.removeExpiredItems();
			if (removed > 0) {
				log.info("Removed {} cart items whose check-in has passed", removed);
			}
		}
		catch (RuntimeException ex) {
			log.error("Cart expiry failed", ex);
		}
	}

}
