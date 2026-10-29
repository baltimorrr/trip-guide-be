package com.company.project.hotel.model;

/** Topic of a hotel policy paragraph (C-HOTEL-03); the hotel page lists them in the hotel's own order. */
public enum HotelPolicyType {
	/** Children of which ages may stay, and on what terms. */
	CHILDREN,
	/** Extra beds and cots: whether they exist and what they cost. */
	EXTRA_BED,
	PETS,
	SMOKING,
	/** ID and deposit asked for at check-in. */
	DEPOSIT,
	/** Payment methods accepted at the property. */
	PAYMENT,
	OTHER
}
