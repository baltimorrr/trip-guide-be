package com.company.project.booking.model;

/** Where a refund is, as Stripe last reported it; PENDING also covers a refund not sent to Stripe yet. */
public enum RefundStatus {
	PENDING,
	SUCCEEDED,
	FAILED
}
