package com.company.project.discount.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.company.project.common.dto.Money;
import com.company.project.common.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A code that takes money off a booking's room subtotal (C-BOOK-03). Read-only until the admin story (A-DISC-01)
 * writes codes; the rules that need no query live here, the usage limits are counted from redemptions.
 * <p>
 * Money amounts (a fixed value, {@code minSpendMinor}, {@code maxDiscountMinor}) are in {@link #currency}, so a code
 * with any of them only applies to prices in that currency. Codes are stored lowercased ({@link #normalize}).
 */
@Entity
@Table(name = "discount_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscountCode extends BaseEntity {

	@Column(nullable = false, length = 50)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(name = "discount_type", nullable = false, length = 10)
	private DiscountType discountType;

	/** A percent, or major units of {@link #currency}. */
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal value;

	@Column(length = 3)
	private String currency;

	@Column(name = "max_discount_minor")
	private Long maxDiscountMinor;

	@Column(name = "min_spend_minor")
	private Long minSpendMinor;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(name = "usage_limit_total")
	private Integer usageLimitTotal;

	@Column(name = "usage_limit_per_user")
	private Integer usageLimitPerUser;

	@Column(name = "first_booking_only", nullable = false)
	private boolean firstBookingOnly;

	@Enumerated(EnumType.STRING)
	@Column(name = "funded_by", nullable = false, length = 10)
	private DiscountFunder fundedBy;

	@Column(name = "applies_to_all_hotels", nullable = false)
	private boolean appliesToAllHotels;

	/** The hotels the code is for when it isn't for all ({@code discount_code_hotels}). */
	@ElementCollection
	@CollectionTable(name = "discount_code_hotels", joinColumns = @JoinColumn(name = "discount_code_id"))
	@Column(name = "hotel_id", nullable = false)
	private Set<Long> hotelIds = new HashSet<>();

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/** The stored form of a typed code: trimmed and lowercased, so codes match in any case. */
	public static String normalize(String code) {
		return code.trim().toLowerCase(Locale.ROOT);
	}

	public boolean hasStarted(Instant now) {
		return !now.isBefore(startsAt);
	}

	public boolean hasEnded(Instant now) {
		return !now.isBefore(endsAt);
	}

	public boolean appliesTo(long hotelId) {
		return appliesToAllHotels || hotelIds.contains(hotelId);
	}

	/** Whether its money amounts can be compared with prices in {@code currency}. */
	public boolean sellsIn(String currency) {
		return this.currency == null || this.currency.equals(currency);
	}

	public boolean isMetBy(long subtotalMinor) {
		return minSpendMinor == null || subtotalMinor >= minSpendMinor;
	}

	/**
	 * What the code takes off {@code subtotalMinor} (minor units of {@code currency}, which it {@linkplain #sellsIn
	 * sells in}): a percent rounded half up and capped by {@link #maxDiscountMinor}, or the fixed amount; never more
	 * than the subtotal.
	 */
	public long amountOff(long subtotalMinor, String currency) {
		long amount = switch (discountType) {
			case PERCENT -> {
				long percent = BigDecimal.valueOf(subtotalMinor).multiply(value)
					.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
					.longValueExact();
				yield maxDiscountMinor == null ? percent : Math.min(percent, maxDiscountMinor);
			}
			case FIXED -> Money.toMinor(value, currency);
		};
		return Math.min(amount, subtotalMinor);
	}

}
