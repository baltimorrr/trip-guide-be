package com.company.project.booking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.company.project.booking.model.Refund;
import com.company.project.booking.model.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** Refunds of bookings' payments. */
public interface RefundRepository extends JpaRepository<Refund, Long> {

	/** The booking's latest refund (a booking is cancelled, and refunded, once). */
	Optional<Refund> findFirstByBookingIdOrderByIdDesc(Long bookingId);

	Optional<Refund> findByStripeRefundId(String stripeRefundId);

	/** With PENDING: up to 100 refunds created before {@code createdBefore} that never reached Stripe, oldest first. */
	List<Refund> findTop100ByStripeRefundIdIsNullAndStatusAndCreatedAtBeforeOrderByCreatedAt(RefundStatus status,
			Instant createdBefore);

}
