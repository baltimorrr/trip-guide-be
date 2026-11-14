package com.company.project.cart.dto;

/**
 * A browser cart item the merge left out.
 *
 * @param index its position in the request's {@code items}
 * @param reason why, as adding it would have answered
 */
public record SkippedCartItem(int index, String reason) {
}
