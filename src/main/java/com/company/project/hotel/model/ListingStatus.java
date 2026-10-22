package com.company.project.hotel.model;

/** Lifecycle of a hotel or room type listing; only ACTIVE ones are shown to travellers. */
public enum ListingStatus {
	DRAFT,
	PENDING_REVIEW,
	ACTIVE,
	INACTIVE,
	SUSPENDED
}
