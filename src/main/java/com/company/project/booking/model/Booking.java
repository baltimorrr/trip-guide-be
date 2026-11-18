package com.company.project.booking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.company.project.common.model.BaseEntity;
import com.company.project.user.model.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;

/**
 * One hotel, one date range and one or more rooms, paid once in the hotel's currency. The totals follow the rooms,
 * discount and taxes added to it, so {@code total = subtotal - discount + taxes on top + fee} always holds (the
 * table checks it too). Status changes are checked and logged by the database (V10's triggers).
 * <p>
 * The display-currency, commission amount and check-in/check-out time columns are not mapped yet: currency
 * conversion (C-SRCH-05), payouts (A-PAY-02) and the owner's booking actions (O-BOOK-03) add what they write.
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

	@Column(nullable = false, length = 12, updatable = false)
	private String reference;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "hotel_id", nullable = false, updatable = false)
	private Long hotelId;

	@Column(name = "check_in", nullable = false)
	private LocalDate checkIn;

	@Column(name = "check_out", nullable = false)
	private LocalDate checkOut;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BookingStatus status;

	@Column(name = "booker_name", nullable = false, length = 100)
	private String bookerName;

	@Column(name = "booker_email", nullable = false, length = 254)
	private String bookerEmail;

	@Column(name = "booker_phone", nullable = false, length = 30)
	private String bookerPhone;

	@Column(name = "booker_country", length = 2)
	private String bookerCountry;

	/** The main guest when the booker books for someone else; null when the booker stays. */
	@Column(name = "guest_name", length = 100)
	private String guestName;

	@Column(name = "special_requests", length = 1000)
	private String specialRequests;

	// A wall-clock time at the hotel, read as-is like Hotel.checkInFrom.
	@JdbcType(LocalTimeJdbcType.class)
	@Column(name = "estimated_arrival")
	private LocalTime estimatedArrival;

	/** When the booker accepted the terms, the privacy policy and the rates' cancellation policies. */
	@Column(name = "terms_accepted_at", nullable = false)
	private Instant termsAcceptedAt;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "subtotal_minor", nullable = false)
	private long subtotalMinor;

	@Column(name = "discount_minor", nullable = false)
	private long discountMinor;

	/** Taxes added on top of the room prices; included taxes are only listed in {@link #taxes}. */
	@Column(name = "tax_minor", nullable = false)
	private long taxMinor;

	@Column(name = "fee_minor", nullable = false)
	private long feeMinor;

	@Column(name = "total_minor", nullable = false)
	private long totalMinor;

	@Column(name = "discount_code_id")
	private Long discountCodeId;

	/** The partner's commission when the booking was made, in percent. */
	@Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal commissionRate;

	/** Until when a Pending booking holds its rooms. */
	@Column(name = "hold_expires_at")
	private Instant holdExpiresAt;

	/** When the payment confirmed it. */
	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	/** The user who cancelled it; null when the system did. */
	@Column(name = "cancelled_by")
	private Long cancelledBy;

	@Column(name = "cancel_reason", length = 500)
	private String cancelReason;

	/** The cancellation policy's penalty when it was cancelled, in minor units of {@link #currency}. */
	@Column(name = "cancellation_fee_minor")
	private Long cancellationFeeMinor;

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
	@OrderBy("id")
	private List<BookingRoom> rooms = new ArrayList<>();

	@OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
	@OrderBy("id")
	private List<BookingTax> taxes = new ArrayList<>();

	/** A Pending booking holding its rooms until {@code holdExpiresAt}; add its rooms, discount and taxes next. */
	public static Booking create(String reference, long userId, long hotelId, LocalDate checkIn, LocalDate checkOut,
			Booker booker, String guestName, String specialRequests, LocalTime estimatedArrival,
			Instant termsAcceptedAt, Instant holdExpiresAt, String currency, BigDecimal commissionRate) {
		Booking booking = new Booking();
		booking.reference = reference;
		booking.userId = userId;
		booking.hotelId = hotelId;
		booking.checkIn = checkIn;
		booking.checkOut = checkOut;
		booking.status = BookingStatus.PENDING;
		booking.bookerName = booker.fullName().trim();
		booking.bookerEmail = User.normalizeEmail(booker.email());
		booking.bookerPhone = booker.phone().trim();
		booking.bookerCountry = booker.countryCode().trim().toUpperCase(Locale.ROOT);
		booking.guestName = trimToNull(guestName);
		booking.specialRequests = trimToNull(specialRequests);
		booking.estimatedArrival = estimatedArrival;
		booking.termsAcceptedAt = termsAcceptedAt;
		booking.holdExpiresAt = holdExpiresAt;
		booking.currency = currency;
		booking.commissionRate = commissionRate;
		return booking;
	}

	public void addRoom(BookingRoom room) {
		room.attachTo(this);
		rooms.add(room);
		subtotalMinor += room.getTotalMinor();
		updateTotal();
	}

	/** At most one code per booking: a second call replaces the first. */
	public void applyDiscount(long discountCodeId, long discountMinor) {
		this.discountCodeId = discountCodeId;
		this.discountMinor = discountMinor;
		updateTotal();
	}

	public void addTax(BookingTax tax) {
		tax.attachTo(this);
		taxes.add(tax);
		if (!tax.isIncluded()) {
			taxMinor += tax.getAmountMinor();
		}
		updateTotal();
	}

	/** Paid (or nothing to pay): the rooms it holds stay booked. Only a Pending booking is confirmed. */
	public void confirm(Instant now) {
		if (status != BookingStatus.PENDING) {
			throw new IllegalStateException("A " + status + " booking can't be confirmed");
		}
		status = BookingStatus.CONFIRMED;
		confirmedAt = now;
	}

	/**
	 * The customer cancelled the whole booking (C-MYBK-03); the caller has given its rooms back and refunds what the
	 * policy allows. Only a Confirmed booking is cancelled this way.
	 *
	 * @param penaltyMinor what the cancellation policy keeps, in minor units of {@link #currency}
	 */
	public void cancel(Instant now, long userId, String reason, long penaltyMinor) {
		if (status != BookingStatus.CONFIRMED) {
			throw new IllegalStateException("A " + status + " booking can't be cancelled");
		}
		status = BookingStatus.CANCELLED;
		cancelledAt = now;
		cancelledBy = userId;
		cancelReason = trimToNull(reason);
		cancellationFeeMinor = penaltyMinor;
		rooms.forEach(room -> room.cancel(now));
	}

	/** The hold ended unpaid; the caller gives the rooms back. Only a Pending booking expires. */
	public void expire() {
		if (status != BookingStatus.PENDING) {
			throw new IllegalStateException("A " + status + " booking can't expire");
		}
		status = BookingStatus.EXPIRED;
	}

	private void updateTotal() {
		totalMinor = subtotalMinor - discountMinor + taxMinor + feeMinor;
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
