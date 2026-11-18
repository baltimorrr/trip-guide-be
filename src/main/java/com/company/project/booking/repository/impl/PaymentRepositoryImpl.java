package com.company.project.booking.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.model.Payment;
import com.company.project.booking.model.PaymentStatus;
import com.company.project.booking.repository.PaymentRepositoryCustom;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** Hand-written payment queries, merged into the {@code PaymentRepository} bean by Spring Data (the "Impl" name). */
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepositoryCustom {

	private static final String OPEN_OF_EXPIRED_HOLDS = """
			select p from Payment p join Booking b on b.id = p.bookingId
			where b.status = :pending and b.holdExpiresAt < :now and p.status in :open
			order by b.holdExpiresAt""";

	private static final String BOOKING_ID = """
			select p.bookingId from Payment p where p.stripePaymentIntentId = :paymentIntentId""";

	private final EntityManager entityManager;

	@Override
	public Optional<Long> findBookingIdByPaymentIntentId(String paymentIntentId) {
		return entityManager.createQuery(BOOKING_ID, Long.class)
			.setParameter("paymentIntentId", paymentIntentId)
			.getResultStream()
			.findFirst();
	}

	@Override
	public List<Payment> findOpenOfExpiredHolds(Instant now, int limit) {
		return entityManager.createQuery(OPEN_OF_EXPIRED_HOLDS, Payment.class)
			.setParameter("pending", BookingStatus.PENDING)
			.setParameter("now", now)
			.setParameter("open", PaymentStatus.OPEN)
			.setMaxResults(limit)
			.getResultList();
	}

}
