package com.company.project.discount.model;

import com.company.project.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One use of a code by a booking; the usage limits count these. An expired hold deletes its row. */
@Entity
@Table(name = "discount_redemptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscountRedemption extends BaseEntity {

	@Column(name = "discount_code_id", nullable = false, updatable = false)
	private Long discountCodeId;

	@Column(name = "booking_id", nullable = false, updatable = false)
	private Long bookingId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "amount_minor", nullable = false)
	private long amountMinor;

	public static DiscountRedemption create(long discountCodeId, long bookingId, long userId, long amountMinor) {
		DiscountRedemption redemption = new DiscountRedemption();
		redemption.discountCodeId = discountCodeId;
		redemption.bookingId = bookingId;
		redemption.userId = userId;
		redemption.amountMinor = amountMinor;
		return redemption;
	}

}
