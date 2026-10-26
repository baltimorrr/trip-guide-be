package com.company.project.search.dto;

/** Values of the {@code cancellation} filter. A refundable rate whose free period has passed is neither. */
public enum CancellationOption {

	/** Free cancellation is still possible for this stay. */
	FREE("Free cancellation"),

	/** The rate's policy is not refundable. */
	NON_REFUNDABLE("Non-refundable");

	private final String label;

	CancellationOption(String label) {
		this.label = label;
	}

	/** Name shown next to the option in the filter list. */
	public String label() {
		return label;
	}

}
