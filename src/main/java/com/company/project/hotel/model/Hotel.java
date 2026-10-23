package com.company.project.hotel.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

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
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;

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

	/** Selling currency (ISO 4217): the hotel's rate plans are priced in it. */
	@Column(nullable = false, length = 3)
	private String currency;

	/** IANA zone of the hotel ("Asia/Ho_Chi_Minh"); check-in times and cancellation deadlines are local to it. */
	@Column(nullable = false, length = 50)
	private String timezone;

	// A wall-clock time in the hotel's zone. Read it as-is: with hibernate.jdbc.time_zone set, the default TIME
	// mapping shifts it from UTC to the JVM's zone (14:00 became 21:00 on a +07:00 machine).
	@JdbcType(LocalTimeJdbcType.class)
	@Column(name = "check_in_from", nullable = false)
	private LocalTime checkInFrom;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ListingStatus status;

	@Column(name = "deleted_at")
	private Instant deletedAt;

}
