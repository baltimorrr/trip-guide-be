package com.company.project.tax.model;

/**
 * One rule's tax on a booking, in minor units of the booking's currency.
 *
 * @param included already inside the room prices: listed, not added to the total
 */
public record TaxLine(Long taxRuleId, String name, boolean included, long amountMinor) {
}
