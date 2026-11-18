package com.company.project.booking.services;

import com.company.project.booking.dto.BookingNotice;
import com.company.project.booking.dto.BookingRequest;
import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.dto.CheckoutQuote;
import com.company.project.booking.dto.CheckoutRequest;

/**
 * Checkout of one cart group, the rooms of one hotel for one date range, into one booking (C-BOOK-01..06). Lines are
 * priced by the hotel page's room list, like the cart, then the discount code and the hotel country's taxes are
 * added. The booking is then paid by card through {@code PaymentService} (C-PAY-01), which confirms it.
 */
public interface BookingService {

	/**
	 * The price breakdown of these cart lines and the booker pre-filled from the profile; nothing is written.
	 * ResourceNotFoundException (404) for a line that isn't in this user's cart. BusinessException 400 for lines of
	 * more than one hotel or date range, or a refused code; 409 when a line can't be booked now (check-in passed, the
	 * rate no longer sells the stay, too few rooms left) or a tax can't be calculated.
	 */
	CheckoutQuote quote(long userId, CheckoutRequest request);

	/**
	 * Books the cart lines as quoted (C-BOOK-01/04/05/06): prices them again under the cart's lock, holds their rooms
	 * for {@code app.booking.hold} and writes a Pending booking with one room row per room booked, then removes the
	 * lines from the cart. Throws like {@link #quote}, and also BusinessException 400 for lead guests that don't
	 * match the lines, 409 when the total isn't {@code expectedTotal} any more (the customer confirms the new one) or
	 * when the rooms were just taken by another booking. Nothing is written when it throws.
	 */
	BookingResponse create(long userId, BookingRequest request);

	/**
	 * The user's booking as its confirmation page shows it (C-PAY-03): hotel address and contact, rooms, guests,
	 * terms, breakdown, payment and, once cancelled, the refund. ResourceNotFoundException (404) for another user's
	 * booking or an unknown reference.
	 */
	BookingResponse find(long userId, String reference);

	/**
	 * The booking and the hotel's contact for its emails, read in a new transaction: call it after the transaction
	 * that changed the booking has committed.
	 */
	BookingNotice notice(long bookingId);

	/**
	 * Expires up to 100 Pending bookings whose hold has passed, oldest first: their rooms go back on sale and their
	 * discount code uses no longer count (C-BOOK-04). Bookings another transaction is changing are left for the next
	 * call. Call it until it returns 0.
	 *
	 * @return how many bookings expired
	 */
	int expireHolds();

}
