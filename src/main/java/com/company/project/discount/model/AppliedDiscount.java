package com.company.project.discount.model;

/** A code that passed every check, and what it takes off the room subtotal in minor units of the booking's currency. */
public record AppliedDiscount(long discountCodeId, String code, long amountMinor) {
}
