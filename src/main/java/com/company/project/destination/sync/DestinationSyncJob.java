package com.company.project.destination.sync;

import com.company.project.destination.services.DestinationSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the destination sync on startup and on a schedule. A failed run is logged and retried at the
 * next tick; it never stops startup or the scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DestinationSyncJob {

	private final DestinationSyncService syncService;

	private final DestinationSyncProperties properties;

	@EventListener(ApplicationReadyEvent.class)
	void syncOnStartup() {
		if (properties.onStartup()) {
			runSafely();
		}
	}

	@Scheduled(cron = "${app.destinations.sync.cron}")
	void syncOnSchedule() {
		runSafely();
	}

	private void runSafely() {
		try {
			syncService.sync();
		}
		catch (RuntimeException ex) {
			log.error("Destination sync failed", ex);
		}
	}

}
