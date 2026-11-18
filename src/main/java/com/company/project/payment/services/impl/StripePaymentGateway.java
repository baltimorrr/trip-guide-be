package com.company.project.payment.services.impl;

import java.util.Locale;
import java.util.Map;

import com.company.project.booking.model.PaymentStatus;
import com.company.project.booking.model.RefundStatus;
import com.company.project.common.exception.BusinessException;
import com.company.project.payment.config.StripeProperties;
import com.company.project.payment.model.PaymentIntentState;
import com.company.project.payment.model.RefundState;
import com.company.project.payment.model.StripeAmounts;
import com.company.project.payment.services.PaymentGateway;
import com.company.project.payment.services.PaymentGatewayException;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * {@link PaymentGateway} on Stripe's API through stripe-java's {@link StripeClient}, which pins the API version
 * (2026-10-28.endive for stripe-java 34.1.0). Requests are retried twice on network errors; the idempotency keys make
 * a retried create return the first result.
 */
@Slf4j
@Service
class StripePaymentGateway implements PaymentGateway {

	private static final String BOOKING_REFERENCE = "booking_reference";

	private static final String REFUND_ID = "refund_id";

	private final StripeClient client;

	StripePaymentGateway(StripeProperties properties) {
		if (properties.hasSecretKey()) {
			client = StripeClient.builder().setApiKey(properties.secretKey()).setMaxNetworkRetries(2).build();
		}
		else {
			client = null;
			log.warn("No Stripe secret key (APP_STRIPE_SECRET_KEY): card payments answer 503");
		}
	}

	@Override
	public PaymentIntentState createPaymentIntent(String bookingReference, long amountMinor, String currency) {
		PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
			.setAmount(StripeAmounts.toStripe(amountMinor, currency))
			.setCurrency(currency.toLowerCase(Locale.ROOT))
			// Cards only for now (C-PAY-06 adds wallets); 3D Secure is requested by Stripe when the bank needs it.
			.addAllowedPaymentMethodType(PaymentIntentCreateParams.AllowedPaymentMethodType.CARD)
			.setDescription("Booking " + bookingReference)
			.putMetadata(BOOKING_REFERENCE, bookingReference)
			.build();
		RequestOptions options = RequestOptions.builder().setIdempotencyKey("payment-" + bookingReference).build();
		try {
			return state(client().v1().paymentIntents().create(params, options));
		}
		catch (InvalidRequestException ex) {
			if ("amount_too_small".equals(ex.getCode())) {
				throw new BusinessException(HttpStatus.CONFLICT,
						"The total is below the smallest amount Stripe can charge");
			}
			throw failed("create a PaymentIntent for " + bookingReference, ex);
		}
		catch (StripeException ex) {
			throw failed("create a PaymentIntent for " + bookingReference, ex);
		}
	}

	@Override
	public PaymentIntentState retrievePaymentIntent(String id) {
		try {
			return state(client().v1().paymentIntents().retrieve(id));
		}
		catch (StripeException ex) {
			throw failed("read PaymentIntent " + id, ex);
		}
	}

	@Override
	public PaymentIntentState cancelPaymentIntent(String id) {
		try {
			return state(client().v1().paymentIntents().cancel(id));
		}
		catch (InvalidRequestException ex) {
			// Already succeeded or canceled: there is nothing to cancel, so its state is the answer.
			if ("payment_intent_unexpected_state".equals(ex.getCode())) {
				return retrievePaymentIntent(id);
			}
			throw failed("cancel PaymentIntent " + id, ex);
		}
		catch (StripeException ex) {
			throw failed("cancel PaymentIntent " + id, ex);
		}
	}

	@Override
	public RefundState createRefund(String paymentIntentId, long amountMinor, String currency,
			String bookingReference, long refundId) {
		RefundCreateParams params = RefundCreateParams.builder()
			.setPaymentIntent(paymentIntentId)
			.setAmount(StripeAmounts.toStripe(amountMinor, currency))
			.setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
			.putMetadata(BOOKING_REFERENCE, bookingReference)
			.putMetadata(REFUND_ID, Long.toString(refundId))
			.build();
		RequestOptions options = RequestOptions.builder().setIdempotencyKey("refund-" + bookingReference).build();
		try {
			return state(client().v1().refunds().create(params, options));
		}
		catch (StripeException ex) {
			throw failed("refund booking " + bookingReference, ex);
		}
	}

	@Override
	public RefundState retrieveRefund(String id) {
		try {
			return state(client().v1().refunds().retrieve(id));
		}
		catch (StripeException ex) {
			throw failed("read refund " + id, ex);
		}
	}

	private StripeClient client() {
		if (client == null) {
			throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Card payments aren't set up on this server");
		}
		return client;
	}

	private static PaymentGatewayException failed(String action, StripeException ex) {
		log.error("Stripe couldn't {} (status {}, code {}, request {}): {}", action, ex.getStatusCode(), ex.getCode(),
				ex.getRequestId(), ex.getMessage());
		return new PaymentGatewayException(ex);
	}

	private static PaymentIntentState state(PaymentIntent intent) {
		String currency = intent.getCurrency().toUpperCase(Locale.ROOT);
		String failure = intent.getLastPaymentError() == null ? null : intent.getLastPaymentError().getMessage();
		return new PaymentIntentState(intent.getId(), status(intent.getStatus(), failure != null),
				StripeAmounts.fromStripe(intent.getAmount(), currency), currency, intent.getClientSecret(), failure,
				metadata(intent.getMetadata(), BOOKING_REFERENCE));
	}

	// https://docs.stripe.com/payments/paymentintents/lifecycle
	private static PaymentStatus status(String stripeStatus, boolean lastAttemptFailed) {
		return switch (stripeStatus) {
			case "requires_payment_method" -> lastAttemptFailed ? PaymentStatus.FAILED : PaymentStatus.REQUIRES_PAYMENT;
			case "processing", "requires_capture" -> PaymentStatus.PROCESSING;
			case "succeeded" -> PaymentStatus.SUCCEEDED;
			case "canceled" -> PaymentStatus.CANCELED;
			// requires_confirmation, requires_action (3D Secure), and any status Stripe adds later
			default -> PaymentStatus.REQUIRES_PAYMENT;
		};
	}

	private static RefundState state(Refund refund) {
		RefundStatus status = switch (refund.getStatus()) {
			case "succeeded" -> RefundStatus.SUCCEEDED;
			case "failed", "canceled" -> RefundStatus.FAILED;
			// pending, requires_action
			default -> RefundStatus.PENDING;
		};
		String refundId = metadata(refund.getMetadata(), REFUND_ID);
		return new RefundState(refund.getId(), status, refundId == null ? null : parseId(refundId),
				refund.getFailureReason());
	}

	private static String metadata(Map<String, String> metadata, String key) {
		return metadata == null ? null : metadata.get(key);
	}

	private static Long parseId(String value) {
		try {
			return Long.valueOf(value);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
