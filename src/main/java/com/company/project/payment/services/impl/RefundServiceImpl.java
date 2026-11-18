package com.company.project.payment.services.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.company.project.booking.model.Booking;
import com.company.project.booking.model.Payment;
import com.company.project.booking.model.Refund;
import com.company.project.booking.model.RefundStatus;
import com.company.project.booking.repository.BookingRepository;
import com.company.project.booking.repository.PaymentRepository;
import com.company.project.booking.repository.RefundRepository;
import com.company.project.payment.model.RefundState;
import com.company.project.payment.services.PaymentGateway;
import com.company.project.payment.services.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stripe is never called inside a transaction: the refund is read, sent, and its answer saved in a transaction of its
 * own. Two sends of one refund (the listener and the retry job) send the same idempotency key and get the same refund.
 */
@Slf4j
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefundServiceImpl implements RefundService {

	/** A refund younger than this may still be on its way from the cancellation's listener. */
	private static final Duration RETRY_AFTER = Duration.ofMinutes(1);

	/** Stripe keeps idempotency keys 24 hours; past this a resend could refund a second time. */
	private static final Duration GIVE_UP_AFTER = Duration.ofHours(23);

	private final RefundRepository refunds;

	private final PaymentRepository payments;

	private final BookingRepository bookings;

	private final PaymentGateway gateway;

	private final TransactionTemplate transactions;

	RefundServiceImpl(RefundRepository refunds, PaymentRepository payments, BookingRepository bookings,
			PaymentGateway gateway, PlatformTransactionManager transactionManager) {
		this.refunds = refunds;
		this.payments = payments;
		this.bookings = bookings;
		this.gateway = gateway;
		// A new transaction: send() also runs right after the cancellation commits, whose resources are still bound.
		this.transactions = new TransactionTemplate(transactionManager);
		this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Override
	public void send(long refundId) {
		Refund refund = refunds.findById(refundId).orElseThrow();
		if (refund.isSent() || refund.getStatus() != RefundStatus.PENDING) {
			return;
		}
		Payment payment = payments.findById(refund.getPaymentId()).orElseThrow();
		Booking booking = bookings.findById(refund.getBookingId()).orElseThrow();
		RefundState state;
		try {
			state = gateway.createRefund(payment.getStripePaymentIntentId(), refund.getAmountMinor(),
					refund.getCurrency(), booking.getReference(), refund.getId());
		}
		catch (RuntimeException ex) {
			log.warn("Refund {} of booking {} didn't reach Stripe; it is sent again within minutes", refundId,
					booking.getReference(), ex);
			return;
		}
		transactions.executeWithoutResult(status -> {
			Refund fresh = refunds.findById(refundId).orElseThrow();
			fresh.markSent(state.id(), state.status());
			logIfFailed(fresh, state);
		});
	}

	@Override
	public int retryUnsent() {
		Instant now = Instant.now();
		List<Refund> unsent = refunds.findTop100ByStripeRefundIdIsNullAndStatusAndCreatedAtBeforeOrderByCreatedAt(
				RefundStatus.PENDING, now.minus(RETRY_AFTER));
		for (Refund refund : unsent) {
			if (refund.getCreatedAt().isBefore(now.minus(GIVE_UP_AFTER))) {
				transactions.executeWithoutResult(status -> refunds.findById(refund.getId()).orElseThrow().fail());
				log.error("Refund {} of {} {} for booking id {} never reached Stripe; refund it by hand in the Stripe "
						+ "dashboard", refund.getId(), refund.getAmountMinor(), refund.getCurrency(), refund.getBookingId());
				continue;
			}
			send(refund.getId());
		}
		return unsent.size();
	}

	@Override
	@Transactional
	public void applyRefund(RefundState state) {
		Optional<Refund> found = refunds.findByStripeRefundId(state.id());
		if (found.isEmpty() && state.refundId() != null) {
			// Stripe made it, but its answer never reached us; the metadata names the row.
			found = refunds.findById(state.refundId()).filter(refund -> !refund.isSent());
			found.ifPresent(refund -> refund.markSent(state.id(), state.status()));
		}
		if (found.isEmpty()) {
			log.warn("Stripe reported refund {} ({}), which this API didn't create; ignored", state.id(), state.status());
			return;
		}
		found.get().apply(state.status());
		logIfFailed(found.get(), state);
	}

	private static void logIfFailed(Refund refund, RefundState state) {
		if (state.status() == RefundStatus.FAILED) {
			log.error("Refund {} ({}) of {} {} for booking id {} failed at Stripe: {}", refund.getId(), state.id(),
					refund.getAmountMinor(), refund.getCurrency(), refund.getBookingId(), state.failureReason());
		}
	}

}
