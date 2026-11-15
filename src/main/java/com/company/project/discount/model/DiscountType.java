package com.company.project.discount.model;

/** How a discount code's {@code value} is read. */
public enum DiscountType {
	/** That percent of the room subtotal, up to the code's cap. */
	PERCENT,
	/** A fixed amount in major units of the code's currency. */
	FIXED
}
