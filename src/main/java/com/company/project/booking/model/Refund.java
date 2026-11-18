package com.company.project.booking.model;

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
 * Money given back on a payment (C-PAY-05). It is written with the cancellation, PENDING and without a Stripe id, then
 * sent to Stripe once that transaction commits; afterwards its status follows Stripe's refund.
 */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {

	@Column(name = "payment_id", nullable = false, updatable = false)
	private Long paymentId;

	@Column(name = "booking_id", nullable = false, updatable = false)
	private Long bookingId;

	@Column(name = "stripe_refund_id")
	private String stripeRefundId;

	@Column(name = "amount_minor", nullable = false, updatable = false)
	private long amountMinor;

	@Column(nullable = false, length = 3, updatable = false)
	private String currency;

	@Column(length = 500, updatable = false)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private RefundStatus status;

	/** Who asked for it; null when the system did. */
	@Column(name = "initiated_by", updatable = false)
	private Long initiatedBy;

	/** A refund of {@code amountMinor} (more than 0) of this payment, still to send to Stripe. */
	public static Refund create(Payment payment, long amountMinor, String reason, Long initiatedBy) {
		if (amountMinor <= 0 || amountMinor > payment.getAmountMinor()) {
			throw new IllegalArgumentException("A refund is more than 0 and at most the payment");
		}
		Refund refund = new Refund();
		refund.paymentId = payment.getId();
		refund.bookingId = payment.getBookingId();
		refund.amountMinor = amountMinor;
		refund.currency = payment.getCurrency();
		refund.reason = reason;
		refund.status = RefundStatus.PENDING;
		refund.initiatedBy = initiatedBy;
		return refund;
	}

	/** Stripe accepted it as {@code stripeRefundId}; {@code status} is what Stripe answered. */
	public void markSent(String stripeRefundId, RefundStatus status) {
		this.stripeRefundId = stripeRefundId;
		this.status = status;
	}

	/** Stripe's latest state of the refund. */
	public void apply(RefundStatus status) {
		this.status = status;
	}

	/** It never reached Stripe and can't be sent again safely; someone refunds it by hand. */
	public void fail() {
		status = RefundStatus.FAILED;
	}

	public boolean isSent() {
		return stripeRefundId != null;
	}

}
