package com.company.project.search.dto;

import com.company.project.common.dto.Money;

/** Lowest and highest price per night of the whole offer among the results, ignoring the price filter itself. */
public record PriceRange(Money min, Money max) {
}
