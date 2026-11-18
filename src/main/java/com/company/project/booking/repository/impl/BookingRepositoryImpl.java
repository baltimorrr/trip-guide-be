package com.company.project.booking.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.model.PaymentStatus;
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
			select b from Booking b where b.status = :pending and b.holdExpiresAt < :now
			and not exists (select 1 from Payment p where p.bookingId = b.id and p.status in :open)
			order by b.holdExpiresAt""";

	private static final String BY_REFERENCE_AND_USER = """
			select b from Booking b where b.reference = :reference and b.userId = :userId""";

	private final EntityManager entityManager;

	@Override
	public List<Booking> findExpiredHoldsForUpdate(Instant now, int limit) {
		return entityManager.createQuery(EXPIRED_HOLDS, Booking.class)
			.setParameter("pending", BookingStatus.PENDING)
			.setParameter("now", now)
			.setParameter("open", PaymentStatus.OPEN)
			.setMaxResults(limit)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.setHint("jakarta.persistence.lock.timeout", Timeouts.SKIP_LOCKED_MILLI)
			.getResultList();
	}

	@Override
	public Optional<Booking> findByReferenceAndUserIdForUpdate(String reference, long userId) {
		return entityManager.createQuery(BY_REFERENCE_AND_USER, Booking.class)
			.setParameter("reference", reference)
			.setParameter("userId", userId)
			.setLockMode(LockModeType.PESSIMISTIC_WRITE)
			.getResultStream()
			.findFirst();
	}

	@Override
	public Optional<Booking> findByIdForUpdate(long id) {
		return Optional.ofNullable(entityManager.find(Booking.class, id, LockModeType.PESSIMISTIC_WRITE));
	}

	@Override
	public void setStatusReason(String reason) {
		setConfig("app.status_reason", reason);
	}

	@Override
	public void setChangedBy(long userId) {
		setConfig("app.changed_by", Long.toString(userId));
	}

	// set_config(..., true) lasts until the transaction ends, so it never leaks into another request's transaction.
	private void setConfig(String name, String value) {
		entityManager.createNativeQuery("select set_config(:name, :value, true)")
			.setParameter("name", name)
			.setParameter("value", value)
			.getSingleResult();
	}

}
