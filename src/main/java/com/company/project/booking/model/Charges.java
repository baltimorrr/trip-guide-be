package com.company.project.booking.model;

import java.util.List;

import com.company.project.discount.model.AppliedDiscount;
import com.company.project.tax.model.TaxLine;

/**
 * What a booking costs, in minor units of the hotel's currency: {@code total = subtotal - discount + taxes on top}.
 *
 * @param discount null without a code
 * @param taxes included taxes too, which are listed but not added
 */
public record Charges(long subtotalMinor, AppliedDiscount discount, List<TaxLine> taxes, long totalMinor) {

	public long discountMinor() {
		return discount == null ? 0 : discount.amountMinor();
	}

}
