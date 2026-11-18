package com.company.project.booking.model;

import java.time.Instant;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A booking's card payment: one Stripe PaymentIntent for the booking's total, in its currency. The status follows
 * Stripe's state of the intent and never leaves SUCCEEDED or CANCELED. The booking owns the row, so its hold expiry and
 * cancellation can read it; the Stripe calls live in the {@code payment} feature.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

	private static final int FAILURE_REASON_LENGTH = 500;

	@Column(name = "booking_id", nullable = false, updatable = false)
	private Long bookingId;

	@Column(nullable = false, length = 20, updatable = false)
	private String provider;

	@Column(name = "stripe_payment_intent_id", updatable = false)
	private String stripePaymentIntentId;

	@Column(name = "amount_minor", nullable = false, updatable = false)
	private long amountMinor;

	@Column(nullable = false, length = 3, updatable = false)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	/** The decline message Stripe gives for the customer; set only while FAILED. */
	@Column(name = "failure_reason", length = FAILURE_REASON_LENGTH)
	private String failureReason;

	@Column(name = "paid_at")
	private Instant paidAt;

	/** A new payment waiting for the customer's card. */
	public static Payment create(long bookingId, String paymentIntentId, long amountMinor, String currency) {
		Payment payment = new Payment();
		payment.bookingId = bookingId;
		payment.provider = "stripe";
		payment.stripePaymentIntentId = paymentIntentId;
		payment.amountMinor = amountMinor;
		payment.currency = currency;
		payment.status = PaymentStatus.REQUIRES_PAYMENT;
		return payment;
	}

	/**
	 * Takes Stripe's latest state of the intent. A final payment (SUCCEEDED, CANCELED) never changes, so a late or
	 * repeated report can't undo it.
	 *
	 * @param failureMessage the decline message, used only for FAILED
	 * @return whether the status changed
	 */
	public boolean apply(PaymentStatus newStatus, String failureMessage, Instant now) {
		if (status.isFinal()) {
			return false;
		}
		boolean changed = newStatus != status;
		status = newStatus;
		failureReason = newStatus == PaymentStatus.FAILED ? truncate(failureMessage) : null;
		if (newStatus == PaymentStatus.SUCCEEDED) {
			paidAt = now;
		}
		return changed;
	}

	/** The customer can still complete it. */
	public boolean isOpen() {
		return PaymentStatus.OPEN.contains(status);
	}

	private static String truncate(String message) {
		if (message == null || message.isBlank()) {
			return null;
		}
		String trimmed = message.trim();
		return trimmed.length() <= FAILURE_REASON_LENGTH ? trimmed : trimmed.substring(0, FAILURE_REASON_LENGTH);
	}

}
