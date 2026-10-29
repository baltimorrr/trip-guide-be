package com.company.project.review.model;

import java.time.Instant;

import com.company.project.common.model.BaseEntity;
import com.company.project.hotel.model.Hotel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer's review of one stay (one per booking), read by the hotel page (C-HOTEL-07). Read-only for now: only
 * customers with a completed stay may write one (C-MYBK-05), which needs bookings, and the migration that creates
 * them adds the foreign key behind {@code bookingId}.
 */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

	@Column(name = "booking_id", nullable = false)
	private Long bookingId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "hotel_id", nullable = false)
	private Hotel hotel;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 1 to 10. */
	@Column(nullable = false)
	private short score;

	@Column(length = 200)
	private String title;

	private String body;

	@Enumerated(EnumType.STRING)
	@Column(name = "traveller_type", length = 20)
	private TravellerType travellerType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReviewStatus status;

	@Column(name = "owner_reply")
	private String ownerReply;

	@Column(name = "owner_replied_at")
	private Instant ownerRepliedAt;

}
