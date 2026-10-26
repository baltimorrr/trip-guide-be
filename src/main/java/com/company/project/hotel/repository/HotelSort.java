package com.company.project.hotel.repository;

import java.util.List;

import com.company.project.common.exception.BusinessException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/** How hotel search results can be sorted (C-SRCH-03). Each sort has a fixed direction. */
public enum HotelSort {

	/** Cheapest offer first; hotels selling in another currency than the price currency come last. */
	PRICE,

	/** Best user rating first, counting only hotels with enough reviews; the others follow. */
	RATING,

	/** Nearest to the centre first; hotels without a distance come last. */
	DISTANCE;

	/**
	 * Reads the {@code sort} query parameter: {@code sort=price}, any case. The direction is ignored.
	 * @return null when no sort was asked for (the default order)
	 * @throws BusinessException 400 for an unknown value or more than one
	 */
	public static HotelSort from(Sort sort) {
		if (sort.isUnsorted()) {
			return null;
		}
		List<Sort.Order> orders = sort.toList();
		if (orders.size() == 1) {
			for (HotelSort value : values()) {
				if (value.name().equalsIgnoreCase(orders.getFirst().getProperty())) {
					return value;
				}
			}
		}
		throw new BusinessException(HttpStatus.BAD_REQUEST, "Sort by one of: price, rating, distance");
	}

}
