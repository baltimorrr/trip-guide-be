package com.company.project.booking.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import com.company.project.hotel.repository.HotelCardView;
import com.company.project.tax.model.TaxCalculator.Night;

/** The cart lines of one hotel and one date range, priced now: what one booking sells, in the hotel's currency. */
public record PricedGroup(HotelCardView hotel, LocalDate checkIn, LocalDate checkOut, String currency,
		List<PricedLine> lines) {

	public long subtotalMinor() {
		return lines.stream().mapToLong(PricedLine::subtotalMinor).sum();
	}

	public int nights() {
		return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
	}

	public int rooms() {
		return lines.stream().mapToInt(priced -> priced.line().quantity()).sum();
	}

	/** Adults and children of every room. */
	public int guests() {
		return lines.stream()
			.mapToInt(priced -> priced.line().quantity()
					* (priced.line().guests().adults() + priced.line().guests().children()))
			.sum();
	}

	/** Each night with what all the rooms cost for it, first night first. */
	public List<Night> nightPrices() {
		return IntStream.range(0, nights())
			.mapToObj(night -> new Night(checkIn.plusDays(night), lines.stream()
				.mapToLong(priced -> priced.rate().nightPricesMinor().get(night) * priced.line().quantity())
				.sum()))
			.toList();
	}

}
