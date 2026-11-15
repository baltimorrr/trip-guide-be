package com.company.project.booking.repository.impl;

import java.time.Instant;
import java.util.List;

import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.repository.BookingRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.hibernate.Timeouts;

/**
 * Hand-written booking queries. Spring Data finds this class by its name ({@code BookingRepository} + "Impl") and
 * merges it into the {@code BookingRepository} bean. JPQL, except {@code set_config()}, which JPQL can't call
 * without a table to select from.
 */
@RequiredArgsConstructor
public class BookingRepositoryImpl implements BookingRepositoryCustom {

	private static final String EXPIRED_HOLDS = """
			select b from Booking b where b.status = :pending and b.holdExpiresAt < :now order by b.holdExpiresAt""";

	private final EntityManager entityManager;

	@Override
	public List<Booking> findExpiredHoldsForUpdate(Instant now, int limit) {
		return entityManager.createQuery(EXPIRED_HOLDS, Booking.class)
			.setParameter("pending", BookingStatus.PENDING)
			.setParameter("now", now)
			.setMaxResults(limit)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.setHint("jakarta.persistence.lock.timeout", Timeouts.SKIP_LOCKED_MILLI)
			.getResultList();
	}

	@Override
	public void setStatusReason(String reason) {
		entityManager.createNativeQuery("select set_config('app.status_reason', :reason, true)")
			.setParameter("reason", reason)
			.getSingleResult();
	}

}
