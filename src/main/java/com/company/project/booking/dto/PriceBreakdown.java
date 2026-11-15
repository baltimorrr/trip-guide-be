package com.company.project.booking.dto;

import java.util.List;

import com.company.project.common.dto.Money;

/**
 * What the booking costs, in the hotel's currency, which is the currency it is charged in:
 * {@code total = subtotal - discount + the taxes that are not included}.
 *
 * @param subtotal every room for the whole stay
 * @param discount null without a code
 * @param taxes by rule (e.g. VAT), included ones too
 */
public record PriceBreakdown(Money subtotal, DiscountAmount discount, List<TaxAmount> taxes, Money total) {
}
