package com.company.project.booking.controller;

import com.company.project.booking.dto.BookingRequest;
import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.dto.CancelBookingRequest;
import com.company.project.booking.dto.CancellationQuote;
import com.company.project.booking.dto.CheckoutQuote;
import com.company.project.booking.dto.CheckoutRequest;
import com.company.project.booking.services.BookingCancellationService;
import com.company.project.booking.services.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	private final BookingCancellationService cancellationService;

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

	/**
	 * Your booking, as its confirmation page shows it (C-PAY-03): the hotel's address and contact, the rooms with
	 * their guests, board and cancellation terms, the price breakdown, the payment and, once cancelled, the refund.
	 * 404 a booking that isn't yours.
	 */
	@GetMapping("/{reference}")
	public BookingResponse find(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
		return bookingService.find(userId(jwt), reference);
	}

	/**
	 * What cancelling your Confirmed booking now costs and refunds (C-MYBK-03): the penalty of each room's policy, and
	 * the rest of what you paid, discount and taxes in proportion. Nothing changes. 404 a booking that isn't yours;
	 * 409 a booking that isn't Confirmed (a Pending one expires on its own).
	 */
	@GetMapping("/{reference}/cancellation")
	public CancellationQuote cancellation(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
		return cancellationService.quote(userId(jwt), reference);
	}

	/**
	 * Cancels your whole booking: its rooms go back on sale and the refund goes to your card (C-PAY-05), its status
	 * shown on the booking. Send the preview's refund as {@code expectedRefund}; when it changed (a penalty started in
	 * between) the answer is 409 with the new refund and nothing changes. 404 and 409 as for the preview.
	 */
	@PostMapping("/{reference}/cancel")
	public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference,
			@Valid @RequestBody CancelBookingRequest request) {
		return cancellationService.cancel(userId(jwt), reference, request);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
