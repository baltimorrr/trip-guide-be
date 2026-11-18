package com.company.project.payment.services;

import com.company.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Stripe couldn't be reached or refused a request; the details are logged where it is thrown. Answers 503. */
public class PaymentGatewayException extends BusinessException {

	public PaymentGatewayException(Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "Card payment is unavailable right now. Try again in a moment");
		initCause(cause);
	}

}
