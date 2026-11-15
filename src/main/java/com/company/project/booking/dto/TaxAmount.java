package com.company.project.booking.dto;

import com.company.project.common.dto.Money;

/** @param included already inside the room prices, so not added to the total */
public record TaxAmount(String name, boolean included, Money amount) {
}
