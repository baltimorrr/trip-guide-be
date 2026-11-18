package com.company.project.payment.services;

import com.company.project.payment.dto.BookingPayment;
import com.company.project.payment.dto.PaymentSession;
import com.company.project.payment.model.PaymentIntentState;

/**
 * Card payment of a Pending booking (C-PAY-01/02): one Stripe PaymentIntent for the booking's total, paid in the
 * customer's browser with Stripe.js. Only Stripe's state of the intent confirms the booking, never the browser.
 */
public interface PaymentService {

	/**
	 * Starts paying the user's booking, or resumes it: the same intent (and client secret) comes back while it can
	 * still be paid, also after a declined card. A booking with nothing to pay is confirmed at once.
	 * ResourceNotFoundException (404) for a booking that isn't the user's; BusinessException 409 when the booking
	 * isn't Pending, its hold has ended, or its payment is already being processed; 503 when Stripe is unavailable.
	 */
	PaymentSession start(long userId, String reference);

	/** The user's booking and its latest payment, as last reported by Stripe. 404 like {@link #start}. */
	BookingPayment status(long userId, String reference);

	/**
	 * Takes Stripe's current state of a PaymentIntent. A success confirms the booking when it is still Pending and the
	 * amount is the booking's; otherwise the payment is recorded and logged as an error for a manual refund. A decline
	 * keeps the booking Pending with Stripe's message. Repeated or out-of-order reports change nothing more. An intent
	 * no booking started is ignored.
	 */
	void applyPaymentIntent(PaymentIntentState state);

	/**
	 * Cancels on Stripe, oldest first, up to 100 open payments of Pending bookings whose hold has passed, so those
	 * bookings can expire (an intent that already succeeded confirms its booking instead). A payment Stripe can't
	 * cancel now is logged and tried again on the next call; its rooms stay held meanwhile.
	 *
	 * @return how many payments were settled
	 */
	int cancelForExpiredHolds();

}
