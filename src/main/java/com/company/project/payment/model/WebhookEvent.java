package com.company.project.payment.model;

/**
 * A Stripe webhook event whose signature checked out.
 *
 * @param id Stripe's event id ({@code evt_…}), the idempotency key
 * @param type e.g. {@code payment_intent.succeeded}
 * @param objectId the id of the object the event is about ({@code data.object.id}), e.g. {@code pi_…}; null when it
 * has none
 */
public record WebhookEvent(String id, String type, String objectId) {
}
