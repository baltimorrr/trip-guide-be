package com.company.project.rate.model;

/** How a cancellation rule's {@code penalty_value} is read. */
public enum PenaltyType {
	/** That percent of the stay's price. */
	PERCENT,
	/** The price of the stay's first nights, that many of them. */
	NIGHTS,
	/** A fixed amount in major units of the rate's currency. */
	FIXED
}
