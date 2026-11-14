package com.company.project.cart.dto;

import java.util.List;

/**
 * The account's cart after a merge, and the browser cart items that couldn't be added.
 *
 * @param skipped in request order; empty when every item made it
 */
public record CartMergeResponse(CartResponse cart, List<SkippedCartItem> skipped) {
}
