package com.company.project.booking.controller;

import com.company.project.booking.dto.BookingRequest;
import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.dto.CheckoutQuote;
import com.company.project.booking.dto.CheckoutRequest;
import com.company.project.booking.services.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout (C-BOOK-01..06): one cart group, the rooms of one hotel for one date range, becomes one booking paid once.
 * The client quotes the group, shows the breakdown and the guest form, then books it.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

	private final BookingService bookingService;

	/**
	 * The price breakdown of these cart lines (rooms, discount, taxes by type, total) and the booker pre-filled from
	 * the profile. 400 lines of more than one hotel or date range, or a refused discount code (the message says
	 * why); 404 a line not in the cart; 409 a line that can't be booked now or taxes that can't be calculated.
	 */
	@PostMapping("/quote")
	public CheckoutQuote quote(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) {
		return bookingService.quote(userId(jwt), request);
	}

	/**
	 * Books the quoted lines: the rooms are held for 15 minutes while the customer pays, and the lines leave the
	 * cart. Send the quote's total as {@code expectedTotal}; when the price changed since, the answer is 409 with the
	 * new total, and the customer confirms it by quoting again. 400 invalid guest details, terms not accepted, lead
	 * guests that don't match the lines; 404 a line not in the cart (also a second submit of the same lines); 409 a
	 * line that can't be booked now or rooms just taken by another booking. Special requests are passed to the hotel
	 * and are not guaranteed.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookingRequest request) {
		return bookingService.create(userId(jwt), request);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
