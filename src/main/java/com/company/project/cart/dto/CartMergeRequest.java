package com.company.project.cart.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** The cart a guest kept in the browser, sent once after signing in. */
public record CartMergeRequest(@NotNull List<@NotNull @Valid CartItemRequest> items) {
}
