package com.company.project.cart.dto;

import java.util.List;

/**
 * The signed-in user's cart, priced on every read (C-CART-02).
 *
 * @param groups one per hotel and date range, by check-in, then hotel name
 */
public record CartResponse(List<CartGroup> groups) {
}
