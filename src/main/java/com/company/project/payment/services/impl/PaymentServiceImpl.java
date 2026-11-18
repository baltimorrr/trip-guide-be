package com.company.project.payment.services.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingConfirmed;
import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.model.Payment;
import com.company.project.booking.model.PaymentStatus;
import com.company.project.booking.repository.BookingRepository;
import com.company.project.booking.repository.PaymentRepository;
import com.company.project.common.exception.BusinessException;
import com.company.project.common.exception.ResourceNotFoundException;
import com.company.project.payment.dto.BookingPayment;
import com.company.project.payment.dto.PaymentSession;
import com.company.project.payment.mapper.PaymentMapper;
import com.company.project.payment.model.PaymentIntentState;
import com.company.project.payment.services.PaymentGateway;
import com.company.project.payment.services.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@Transactional(readOnly = true)
class PaymentServiceImpl implements PaymentService {

	private static final int EXPIRY_BATCH = 100;

	private final BookingRepository bookings;

	private final PaymentRepository payments;

	private final PaymentGateway gateway;

	private final PaymentMapper mapper;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transactions;

	PaymentServiceImpl(BookingRepository bookings, PaymentRepository payments, PaymentGateway gateway,
			PaymentMapper mapper, ApplicationEventPublisher events, PlatformTransactionManager transactionManager) {
		this.bookings = bookings;
		this.payments = payments;
		this.gateway = gateway;
		this.mapper = mapper;
		this.events = events;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	// Stripe is called while the booking row is locked, on purpose: two starts of one booking can't create two
	// intents. If this transaction then rolls back, the next start sends the same idempotency key and gets the same
	// intent back; an intent nobody has the client secret of can't be charged.
	@Override
	@Transactional
	public PaymentSession start(long userId, String reference) {
		Booking booking = bookings.findByReferenceAndUserIdForUpdate(reference, userId)
			.orElseThrow(() -> new ResourceNotFoundException("Booking", reference));
		Instant now = Instant.now();
		checkPayable(booking, now);
		if (booking.getTotalMinor() == 0) {
			bookings.setStatusReason("Nothing to pay");
			booking.confirm(now);
			bookings.flush();
			events.publishEvent(new BookingConfirmed(booking.getId()));
			return mapper.toSession(booking, null, null);
		}
		Optional<Payment> open = payments.findFirstByBookingIdAndStatusIn(booking.getId(), PaymentStatus.OPEN);
		if (open.isPresent()) {
			PaymentIntentState intent = gateway.retrievePaymentIntent(open.get().getStripePaymentIntentId());
			if (intent.status() != PaymentStatus.REQUIRES_PAYMENT && intent.status() != PaymentStatus.FAILED) {
				throw new BusinessException(HttpStatus.CONFLICT,
						"This payment is being processed. Check its status in a moment");
			}
			return mapper.toSession(booking, open.get(), intent.clientSecret());
		}
		PaymentIntentState intent = gateway.createPaymentIntent(reference, booking.getTotalMinor(),
				booking.getCurrency());
		Payment payment = payments.saveAndFlush(
				Payment.create(booking.getId(), intent.id(), booking.getTotalMinor(), booking.getCurrency()));
		return mapper.toSession(booking, payment, intent.clientSecret());
	}

	@Override
	public BookingPayment status(long userId, String reference) {
		Booking booking = bookings.findByReferenceAndUserId(reference, userId)
			.orElseThrow(() -> new ResourceNotFoundException("Booking", reference));
		return mapper.toStatus(booking, payments.findFirstByBookingIdOrderByIdDesc(booking.getId()).orElse(null));
	}

	@Override
	@Transactional
	public void applyPaymentIntent(PaymentIntentState state) {
		apply(state);
	}

	// Stripe is called outside any transaction, so no row stays locked while it answers; each answer is applied in
	// its own transaction.
	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public int cancelForExpiredHolds() {
		List<Payment> open = payments.findOpenOfExpiredHolds(Instant.now(), EXPIRY_BATCH);
		int settled = 0;
		for (Payment payment : open) {
			try {
				PaymentIntentState state = gateway.cancelPaymentIntent(payment.getStripePaymentIntentId());
				transactions.executeWithoutResult(status -> apply(state));
				settled++;
			}
			catch (RuntimeException ex) {
				log.error("Couldn't cancel PaymentIntent {} of booking {} after its hold ended; its rooms stay held "
						+ "until the next try", payment.getStripePaymentIntentId(), payment.getBookingId(), ex);
			}
		}
		return settled;
	}

	private void apply(PaymentIntentState state) {
		Optional<Long> bookingId = payments.findBookingIdByPaymentIntentId(state.id());
		if (bookingId.isEmpty()) {
			log.warn("Stripe reported PaymentIntent {} ({}), which no booking started; ignored", state.id(),
					state.status());
			return;
		}
		// The booking row first, as every booking write does; then the payment is read fresh.
		Booking booking = bookings.findByIdForUpdate(bookingId.get()).orElseThrow();
		Payment payment = payments.findByStripePaymentIntentId(state.id()).orElseThrow();
		PaymentStatus before = payment.getStatus();
		Instant now = Instant.now();
		payment.apply(state.status(), state.failureMessage(), now);
		if (state.status() != PaymentStatus.SUCCEEDED || before == PaymentStatus.SUCCEEDED) {
			return;
		}
		boolean amountMatches = before != PaymentStatus.CANCELED && state.amountMinor() == payment.getAmountMinor()
				&& state.currency().equals(payment.getCurrency());
		if (booking.getStatus() == BookingStatus.PENDING && amountMatches) {
			bookings.setStatusReason("Paid by card");
			booking.confirm(now);
			bookings.flush();
			events.publishEvent(new BookingConfirmed(booking.getId()));
			return;
		}
		log.error("PaymentIntent {} succeeded with {} {} for booking {} ({}, payment {} {} was {}): the booking was not "
				+ "confirmed, refund the payment in the Stripe dashboard", state.id(), state.amountMinor(),
				state.currency(), booking.getReference(), booking.getStatus(), payment.getAmountMinor(),
				payment.getCurrency(), before);
	}

	private static void checkPayable(Booking booking, Instant now) {
		switch (booking.getStatus()) {
			case PENDING -> {
				if (!booking.getHoldExpiresAt().isAfter(now)) {
					throw new BusinessException(HttpStatus.CONFLICT,
							"The rooms are no longer held for this booking. Book them again");
				}
			}
			case EXPIRED -> throw new BusinessException(HttpStatus.CONFLICT,
					"The rooms are no longer held for this booking. Book them again");
			case CANCELLED -> throw new BusinessException(HttpStatus.CONFLICT, "This booking was cancelled");
			default -> throw new BusinessException(HttpStatus.CONFLICT, "This booking is already paid");
		}
	}

}
