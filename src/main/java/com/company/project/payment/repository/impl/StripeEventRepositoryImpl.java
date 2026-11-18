package com.company.project.payment.repository.impl;

import java.time.Instant;

import com.company.project.payment.repository.StripeEventRepositoryCustom;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Stripe event log's writes, merged into the {@code StripeEventRepository} bean by Spring Data (the "Impl" name).
 * Native SQL for the insert: the payload is bound as text and cast to {@code jsonb}, which JPQL can't express.
 */
@RequiredArgsConstructor
public class StripeEventRepositoryImpl implements StripeEventRepositoryCustom {

	private static final int ERROR_LENGTH = 1000;

	private static final String INSERT = """
			insert into stripe_events (id, type, payload, received_at)
			values (:id, :type, cast(:payload as jsonb), :receivedAt)
			on conflict (id) do nothing""";

	private static final String PROCESSED = """
			update StripeEvent e set e.processedAt = :processedAt, e.error = null where e.id = :id""";

	private static final String FAILED = "update StripeEvent e set e.error = :error where e.id = :id";

	private final EntityManager entityManager;

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertIfAbsent(String id, String type, String payload, Instant receivedAt) {
		entityManager.createNativeQuery(INSERT)
			.setParameter("id", id)
			.setParameter("type", type)
			.setParameter("payload", payload)
			.setParameter("receivedAt", receivedAt)
			.executeUpdate();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markProcessed(String id, Instant processedAt) {
		entityManager.createQuery(PROCESSED).setParameter("id", id).setParameter("processedAt", processedAt)
			.executeUpdate();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(String id, String error) {
		String cut = error == null || error.length() <= ERROR_LENGTH ? error : error.substring(0, ERROR_LENGTH);
		entityManager.createQuery(FAILED).setParameter("id", id).setParameter("error", cut).executeUpdate();
	}

}
