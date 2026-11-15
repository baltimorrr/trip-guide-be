package com.company.project.booking.expiry;

import com.company.project.booking.services.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires Pending bookings whose room hold has passed unpaid and puts their rooms back on sale (C-BOOK-04), on
 * {@code app.booking.expiry-cron}, a batch at a time until none is left. A failed run is logged and retried at the
 * next tick; running on several instances at once is harmless, as each skips the bookings another has locked.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BookingHoldExpiryJob {

	private final BookingService bookingService;

	@Scheduled(cron = "${app.booking.expiry-cron}")
	void expireHolds() {
		try {
			int expired = 0;
			for (int batch = bookingService.expireHolds(); batch > 0; batch = bookingService.expireHolds()) {
				expired += batch;
			}
			if (expired > 0) {
				log.info("Expired {} unpaid bookings and released their rooms", expired);
			}
		}
		catch (RuntimeException ex) {
			log.error("Booking hold expiry failed", ex);
		}
	}

}
