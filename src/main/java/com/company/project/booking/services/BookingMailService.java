package com.company.project.booking.services;

import com.company.project.booking.dto.BookingResponse;

/**
 * A booking's emails to the customer and to the hotel. Each carries the booking as its page shows it: reference,
 * hotel name, address and contact, dates, rooms, guests, board, cancellation terms and price breakdown (C-PAY-03), plus
 * the penalty and refund once cancelled (C-MYBK-03). Called after the transaction that changed the booking has
 * committed, never inside it.
 */
public interface BookingMailService {

	/** The customer's confirmation of a paid booking. */
	void sendConfirmation(String email, BookingResponse booking);

	/** Tells the hotel about a new confirmed booking (O-BOOK-02). */
	void notifyOwnerOfNewBooking(String email, BookingResponse booking);

	/** The customer's notice of a cancellation, with the penalty and the refund. */
	void sendCancellation(String email, BookingResponse booking);

	/** Tells the hotel a booking was cancelled (O-BOOK-02). */
	void notifyOwnerOfCancellation(String email, BookingResponse booking);

}
