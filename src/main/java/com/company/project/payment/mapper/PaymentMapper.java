package com.company.project.payment.mapper;

import java.time.Instant;

import com.company.project.booking.mapper.BookingMapper;
import com.company.project.booking.model.Booking;
import com.company.project.booking.model.BookingStatus;
import com.company.project.booking.model.Payment;
import com.company.project.common.dto.Money;
import com.company.project.payment.dto.BookingPayment;
import com.company.project.payment.dto.PaymentSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts a booking and its payment into the payment endpoints' responses. */
@Component
@RequiredArgsConstructor
public class PaymentMapper {

	private final BookingMapper bookingMapper;

	/** {@code payment} and {@code clientSecret} null when there was nothing to pay. */
	public PaymentSession toSession(Booking booking, Payment payment, String clientSecret) {
		return new PaymentSession(booking.getReference(), booking.getStatus(), amount(booking), clientSecret,
				payment == null ? null : payment.getStatus(), payBy(booking));
	}

	public BookingPayment toStatus(Booking booking, Payment payment) {
		return new BookingPayment(booking.getReference(), booking.getStatus(), payBy(booking), amount(booking),
				bookingMapper.toPaymentDetails(payment));
	}

	private static Money amount(Booking booking) {
		return Money.ofMinor(booking.getTotalMinor(), booking.getCurrency());
	}

	private static Instant payBy(Booking booking) {
		return booking.getStatus() == BookingStatus.PENDING ? booking.getHoldExpiresAt() : null;
	}

}
