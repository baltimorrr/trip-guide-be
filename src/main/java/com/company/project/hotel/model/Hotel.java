package com.company.project.hotel.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A hotel listing, read by search and the homepage. Hotels are not created through JPA yet, so this maps
 * only the columns those queries read; the hotel owner stories (O-HOTEL-*) add the rest with the writes.
 */
@Entity
@Table(name = "hotels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hotel extends BaseEntity {

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 220)
	private String slug;

	@Column(name = "star_rating")
	private Short starRating;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "address_id", nullable = false)
	private Address address;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "accommodation_type_id", nullable = false)
	private AccommodationType accommodationType;

	@Column(name = "distance_to_center_m")
	private Integer distanceToCenterM;

	@Column(name = "google_rating", precision = 2, scale = 1)
	private BigDecimal googleRating;

	@Column(name = "google_rating_count")
	private Integer googleRatingCount;

	@Column(name = "user_rating_avg", precision = 3, scale = 1)
	private BigDecimal userRatingAvg;

	@Column(name = "user_rating_count", nullable = false)
	private int userRatingCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ListingStatus status;

	@Column(name = "deleted_at")
	private Instant deletedAt;

}
