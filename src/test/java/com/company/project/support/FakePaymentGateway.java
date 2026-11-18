package com.company.project.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.company.project.booking.model.PaymentStatus;
import com.company.project.booking.model.RefundStatus;
import com.company.project.payment.model.PaymentIntentState;
import com.company.project.payment.model.RefundState;
import com.company.project.payment.services.PaymentGateway;
import com.company.project.payment.services.PaymentGatewayException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stripe for tests: intents and refunds live in memory and behave like Stripe's for the calls this project makes,
 * idempotency per booking included. Tests move an intent along ({@link #succeed}, {@link #decline}) and then send the
 * webhook Stripe would send. Every {@link IntegrationTest} gets it through {@link Config}; tests use their own
 * bookings, so they never read each other's intents.
 */
public class FakePaymentGateway implements PaymentGateway {

	private static final AtomicLong IDS = new AtomicLong();

	private final Map<String, PaymentIntentState> intents = new ConcurrentHashMap<>();

	private final Map<String, String> intentByReference = new ConcurrentHashMap<>();

	private final Map<String, AtomicInteger> intentCreates = new ConcurrentHashMap<>();

	private final Map<String, RefundState> refunds = new ConcurrentHashMap<>();

	private final Map<String, String> refundByReference = new ConcurrentHashMap<>();

	private final Map<String, Long> refundAmounts = new ConcurrentHashMap<>();

	private final AtomicBoolean failNext = new AtomicBoolean();

	@Override
	public PaymentIntentState createPaymentIntent(String bookingReference, long amountMinor, String currency) {
		failIfAsked();
		intentCreates.computeIfAbsent(bookingReference, key -> new AtomicInteger()).incrementAndGet();
		String id = intentByReference.computeIfAbsent(bookingReference, key -> {
			String newId = "pi_fake_" + IDS.incrementAndGet();
			intents.put(newId, new PaymentIntentState(newId, PaymentStatus.REQUIRES_PAYMENT, amountMinor, currency,
					newId + "_secret_fake", null, bookingReference));
			return newId;
		});
		return intents.get(id);
	}

	@Override
	public PaymentIntentState retrievePaymentIntent(String id) {
		failIfAsked();
		return intent(id);
	}

	@Override
	public PaymentIntentState cancelPaymentIntent(String id) {
		failIfAsked();
		PaymentIntentState intent = intent(id);
		return intent.status().isFinal() ? intent : set(intent, PaymentStatus.CANCELED, null);
	}

	@Override
	public RefundState createRefund(String paymentIntentId, long amountMinor, String currency,
			String bookingReference, long refundId) {
		failIfAsked();
		String id = refundByReference.computeIfAbsent(bookingReference, key -> {
			String newId = "re_fake_" + IDS.incrementAndGet();
			refunds.put(newId, new RefundState(newId, RefundStatus.SUCCEEDED, refundId, null));
			refundAmounts.put(newId, amountMinor);
			return newId;
		});
		return refunds.get(id);
	}

	@Override
	public RefundState retrieveRefund(String id) {
		failIfAsked();
		RefundState refund = refunds.get(id);
		if (refund == null) {
			throw new AssertionError("No fake refund " + id);
		}
		return refund;
	}

	/** The customer paid: the intent succeeded. */
	public void succeed(String intentId) {
		set(intent(intentId), PaymentStatus.SUCCEEDED, null);
	}

	/** The card was declined with Stripe's {@code message}; the intent can be retried. */
	public void decline(String intentId, String message) {
		set(intent(intentId), PaymentStatus.FAILED, message);
	}

	/** Stripe reports the refund failed (e.g. the card was closed). */
	public void failRefund(String refundId) {
		RefundState refund = retrieveRefund(refundId);
		refunds.put(refundId, new RefundState(refundId, RefundStatus.FAILED, refund.refundId(), "expired_or_canceled_card"));
	}

	/** The next call to Stripe fails as if Stripe were unreachable. */
	public void failNextCall() {
		failNext.set(true);
	}

	public PaymentIntentState intent(String id) {
		PaymentIntentState intent = intents.get(id);
		if (intent == null) {
			throw new AssertionError("No fake PaymentIntent " + id);
		}
		return intent;
	}

	/** The intent created for this booking, or null. */
	public String intentIdOf(String bookingReference) {
		return intentByReference.get(bookingReference);
	}

	/** How many times a PaymentIntent was asked for this booking. */
	public int intentCreates(String bookingReference) {
		AtomicInteger creates = intentCreates.get(bookingReference);
		return creates == null ? 0 : creates.get();
	}

	/** The refund created for this booking, or null. */
	public String refundIdOf(String bookingReference) {
		return refundByReference.get(bookingReference);
	}

	public long refundAmount(String refundId) {
		return refundAmounts.get(refundId);
	}

	/** Every refund this fake holds; their ids. */
	public List<String> refundIds() {
		return List.copyOf(refunds.keySet());
	}

	private PaymentIntentState set(PaymentIntentState intent, PaymentStatus status, String failureMessage) {
		PaymentIntentState changed = new PaymentIntentState(intent.id(), status, intent.amountMinor(),
				intent.currency(), intent.clientSecret(), failureMessage, intent.bookingReference());
		intents.put(intent.id(), changed);
		return changed;
	}

	private void failIfAsked() {
		if (failNext.getAndSet(false)) {
			throw new PaymentGatewayException(new IllegalStateException("Fake Stripe outage"));
		}
	}

	/** Replaces the Stripe gateway in the shared test context. */
	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		FakePaymentGateway fakePaymentGateway() {
			return new FakePaymentGateway();
		}

	}

}
