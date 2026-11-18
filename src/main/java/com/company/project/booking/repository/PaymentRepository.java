package com.company.project.booking.repository;

import java.util.Collection;
import java.util.Optional;

import com.company.project.booking.model.Payment;
import com.company.project.booking.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/** Bookings' card payments. Hand-written queries are in {@link PaymentRepositoryCustom}. */
public interface PaymentRepository extends JpaRepository<Payment, Long>, PaymentRepositoryCustom {

	/** With {@link PaymentStatus#OPEN}: the payment the customer can still complete, if any (at most one exists). */
	Optional<Payment> findFirstByBookingIdAndStatusIn(Long bookingId, Collection<PaymentStatus> statuses);

	/** The booking's latest payment. */
	Optional<Payment> findFirstByBookingIdOrderByIdDesc(Long bookingId);

	Optional<Payment> findByStripePaymentIntentId(String paymentIntentId);

}
