package com.company.project.booking.dto;

import com.company.project.booking.model.RefundStatus;
import com.company.project.common.dto.Money;

/** The money a cancellation gives back to the card, and where Stripe is with it (C-PAY-05). */
public record RefundDetails(Money amount, RefundStatus status) {
}
