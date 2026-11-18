package com.company.project.payment.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A Stripe webhook event as received, once per event id. Not a {@code BaseEntity}: Stripe's id is the key, and the row
 * is only inserted (by a query that ignores a repeat) and then marked processed or failed.
 */
@Entity
@Table(name = "stripe_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StripeEvent {

	@Id
	private String id;

	@Column(nullable = false, length = 100)
	private String type;

	/** The event exactly as Stripe sent it. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	/** Why processing failed the last time; Stripe sends the event again. */
	@Column(length = 1000)
	private String error;

}
