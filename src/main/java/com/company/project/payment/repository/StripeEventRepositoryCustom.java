package com.company.project.payment.repository;

import java.time.Instant;

/**
 * Writes to the Stripe event log, implemented in {@code impl/StripeEventRepositoryImpl}. Each runs in its own
 * transaction, so a failure while handling an event still leaves its error on the row.
 */
public interface StripeEventRepositoryCustom {

	/** Logs the event once: a repeat (or a concurrent copy) of the same event id writes nothing. */
	void insertIfAbsent(String id, String type, String payload, Instant receivedAt);

	void markProcessed(String id, Instant processedAt);

	/** {@code error} is cut to the column's 1000 characters. */
	void markFailed(String id, String error);

}
