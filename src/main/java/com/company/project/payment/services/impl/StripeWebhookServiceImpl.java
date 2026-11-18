package com.company.project.payment.services.impl;

import java.time.Instant;

import com.company.project.payment.model.WebhookEvent;
import com.company.project.payment.repository.StripeEventRepository;
import com.company.project.payment.services.PaymentGateway;
import com.company.project.payment.services.PaymentService;
import com.company.project.payment.services.RefundService;
import com.company.project.payment.services.StripeWebhookService;
import com.company.project.payment.services.StripeWebhookVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Not transactional: the event log's writes commit on their own, and each state change runs in the transaction of the
 * service that applies it. The event's own copy of the object is never trusted; the object is read back from Stripe,
 * which also makes the order of delivery irrelevant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class StripeWebhookServiceImpl implements StripeWebhookService {

	private final StripeWebhookVerifier verifier;

	private final StripeEventRepository stripeEvents;

	private final PaymentGateway gateway;

	private final PaymentService payments;

	private final RefundService refunds;

	@Override
	public void handle(String payload, String signatureHeader) {
		WebhookEvent event = verifier.verify(payload, signatureHeader);
		stripeEvents.insertIfAbsent(event.id(), event.type(), payload, Instant.now());
		if (stripeEvents.existsByIdAndProcessedAtIsNotNull(event.id())) {
			log.debug("Stripe event {} was already handled", event.id());
			return;
		}
		try {
			dispatch(event);
			stripeEvents.markProcessed(event.id(), Instant.now());
		}
		catch (RuntimeException ex) {
			stripeEvents.markFailed(event.id(), ex.toString());
			log.error("Stripe event {} ({}) about {} failed; Stripe will send it again", event.id(), event.type(),
					event.objectId(), ex);
			throw ex;
		}
	}

	private void dispatch(WebhookEvent event) {
		switch (event.type()) {
			case "payment_intent.succeeded", "payment_intent.payment_failed", "payment_intent.canceled",
					"payment_intent.processing" -> {
				if (event.objectId() == null) {
					log.warn("Stripe event {} ({}) names no PaymentIntent; ignored", event.id(), event.type());
					return;
				}
				payments.applyPaymentIntent(gateway.retrievePaymentIntent(event.objectId()));
			}
			case "refund.created", "refund.updated", "refund.failed" -> {
				if (event.objectId() == null) {
					log.warn("Stripe event {} ({}) names no refund; ignored", event.id(), event.type());
					return;
				}
				refunds.applyRefund(gateway.retrieveRefund(event.objectId()));
			}
			default -> log.info("Stripe event {} ({}) is not used; ignored", event.id(), event.type());
		}
	}

}
