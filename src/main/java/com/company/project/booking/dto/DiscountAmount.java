package com.company.project.booking.dto;

import com.company.project.common.dto.Money;

/** The code applied and what it takes off the room subtotal. */
public record DiscountAmount(String code, Money amount) {
}
