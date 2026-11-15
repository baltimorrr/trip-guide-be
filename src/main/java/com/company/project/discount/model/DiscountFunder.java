package com.company.project.discount.model;

/** Who pays for a code's discount; read by payouts (A-PAY-02), not by checkout. */
public enum DiscountFunder {
	PLATFORM,
	HOTEL,
	SHARED
}
