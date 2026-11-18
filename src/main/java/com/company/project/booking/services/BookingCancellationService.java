package com.company.project.booking.services;

import com.company.project.booking.dto.BookingResponse;
import com.company.project.booking.dto.CancelBookingRequest;
import com.company.project.booking.dto.CancellationQuote;

/**
 * The customer cancels a whole Confirmed booking (C-MYBK-03) and gets back what its policies allow (C-PAY-05). One room
 * of a booking can't be cancelled on its own, and a Pending booking isn't cancelled: it expires when its hold ends.
 * Both methods throw ResourceNotFoundException (404) for another user's booking and BusinessException 409 for a booking
 * that isn't Confirmed.
 */
public interface BookingCancellationService {

	/** The penalty and the refund if the booking were cancelled now; nothing is written. */
	CancellationQuote quote(long userId, String reference);

	/**
	 * Cancels the booking: its rooms go back on sale, the penalty is kept and the rest is refunded to the card through
	 * Stripe once this commits (the refund's status shows on the booking). BusinessException 409, with nothing
	 * written, when the refund isn't {@code expectedRefund} any more.
	 */
	BookingResponse cancel(long userId, String reference, CancelBookingRequest request);

}
