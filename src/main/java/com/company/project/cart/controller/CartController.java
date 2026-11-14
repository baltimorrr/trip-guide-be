package com.company.project.cart.controller;

import com.company.project.cart.dto.CartItemRequest;
import com.company.project.cart.dto.CartItemUpdateRequest;
import com.company.project.cart.dto.CartMergeRequest;
import com.company.project.cart.dto.CartMergeResponse;
import com.company.project.cart.dto.CartResponse;
import com.company.project.cart.services.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's cart (C-CART-01/02/03): rooms of any hotel and dates, grouped by hotel and date range, each group
 * one future booking. Every answer is priced now. Guests keep their cart in the browser and merge it in after
 * signing in.
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

	private final CartService cartService;

	/** Every line with its status, current price and the price when it was picked; an empty list of groups when none. */
	@GetMapping
	public CartResponse cart(@AuthenticationPrincipal Jwt jwt) {
		return cartService.cart(userId(jwt));
	}

	/**
	 * Adds rooms, or adds them to the line with the same rate, stay and guests; answers the cart. 400 invalid stay or
	 * guests the room can't hold, 404 unknown rate plan, 409 the rate can't sell the stay, too few rooms left or the
	 * cart is full.
	 */
	@PostMapping("/items")
	public CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartItemRequest request) {
		return cartService.add(userId(jwt), request);
	}

	/**
	 * New dates and number of rooms for a line; answers the cart. New dates keep the rate and guests, and when they
	 * match another line the two become one. Errors as for adding, and 404 when the line isn't in this cart.
	 */
	@PutMapping("/items/{itemId}")
	public CartResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId,
			@Valid @RequestBody CartItemUpdateRequest request) {
		return cartService.update(userId(jwt), itemId, request);
	}

	/** 204 whether or not the line was in this cart. */
	@DeleteMapping("/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
		cartService.remove(userId(jwt), itemId);
	}

	/**
	 * The guest's browser cart, sent once after signing in. A line already in the cart keeps the larger quantity, so
	 * sending it again changes nothing. Items that can't be added are listed in {@code skipped} with the reason; 400
	 * for more items than a cart holds.
	 */
	@PostMapping("/merge")
	public CartMergeResponse merge(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartMergeRequest request) {
		return cartService.merge(userId(jwt), request);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
