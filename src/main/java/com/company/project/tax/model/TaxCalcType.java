package com.company.project.tax.model;

/** How a tax rule's {@code value} turns into an amount. */
public enum TaxCalcType {
	/** That percent of the room price. */
	PERCENT,
	/** The value for each room and night. */
	PER_NIGHT,
	/** The value once per booking. */
	PER_BOOKING,
	/** The value for each guest (adults and children) and night. */
	PER_PERSON_NIGHT
}
