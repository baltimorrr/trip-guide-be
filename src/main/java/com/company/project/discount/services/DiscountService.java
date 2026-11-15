package com.company.project.discount.services;

import java.util.Collection;

import com.company.project.discount.model.AppliedDiscount;
import com.company.project.discount.model.DiscountUse;

/**
 * Discount codes at checkout (C-BOOK-03): one code per booking, taken off the room subtotal. Codes are matched trimmed
 * and in any case. A refused code throws BusinessException 400 with the reason, checked in this order: unknown,
 * inactive or deleted; not started; ended; another hotel; another currency; minimum spend; used up; used by this
 * user; first booking only.
 */
public interface DiscountService {

	/** What the code takes off this booking, for a quote. */
	AppliedDiscount check(DiscountUse use);

	/**
	 * Like {@link #check}, with the code row-locked until the caller's transaction ends, so concurrent bookings can't
	 * use it past its limits. Call {@link #redeem} in the same transaction.
	 */
	AppliedDiscount reserve(DiscountUse use);

	/** Records the use, which the limits count from now on. */
	void redeem(AppliedDiscount discount, long bookingId, long userId);

	/** Forgets these bookings' uses (they never happened: the hold expired), so the limits no longer count them. */
	void releaseUses(Collection<Long> bookingIds);

}
