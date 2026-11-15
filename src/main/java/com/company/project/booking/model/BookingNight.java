package com.company.project.booking.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One row of {@code booking_room_nights}: what one night of a booked room cost. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingNight {

	@Column(name = "stay_date", nullable = false)
	private LocalDate stayDate;

	@Column(name = "price_minor", nullable = false)
	private long priceMinor;

	public BookingNight(LocalDate stayDate, long priceMinor) {
		this.stayDate = stayDate;
		this.priceMinor = priceMinor;
	}

}
