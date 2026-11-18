---
phase: 3
title: Webhook confirmation and hold expiry
status: completed
priority: P1
dependencies:
  - 2
---

# Phase 3: Webhook confirmation and hold expiry

## Overview

Stripe's signed webhook confirms paid bookings and records declines (C-PAY-01, C-PAY-02). The hold expiry first
cancels the booking's open PaymentIntent, so a booking only expires and releases its rooms once Stripe can no longer
charge it (C-PAY-02 "after the hold expires it becomes Expired").

## Requirements

- Functional:
  - `POST /api/payments/stripe/webhook` is public and `@Hidden` from OpenAPI. It reads the raw body and the
    `Stripe-Signature` header:
    - bad or missing signature → 400
    - handled → 200
    - processing error → 500, so Stripe retries
  - Handled event types:
    - `payment_intent.succeeded`, `payment_intent.payment_failed`, `payment_intent.canceled` and
      `payment_intent.processing`
    - `refund.*`, from phase 5
    - any other type: logged and answered 200
  - On success, a Pending booking becomes Confirmed:
    - `confirmed_at` is set, and the history row says "Paid by card", by the system
    - the payment becomes SUCCEEDED with `paid_at`
  - A decline leaves the booking Pending. The payment becomes FAILED, with Stripe's customer-facing message as
    `failure_reason`.
  - Success for a booking that is no longer Pending, or an amount or currency that differs from the payment row:
    - the payment is recorded SUCCEEDED, and the booking is left as it is
    - an ERROR is logged: "Payment {pi} for booking {reference} ({status}) needs a manual refund" (user decision)
  - An intent with no payment row (another integration, or `stripe trigger`) → WARN, 200.
  - Expiry: every tick, up to 100 open payments of Pending bookings whose hold passed are canceled on Stripe and the
    answer is applied:
    - canceled → the payment is CANCELED
    - succeeded → the booking is confirmed
    - a Stripe error → logged, retried next tick, and the booking stays held
  - `expireHolds` skips bookings with an open payment.
- Non-functional:
  - A repeated event changes nothing (idempotency).
  - Events arriving out of order end in Stripe's current state.
  - A failed event is logged at ERROR and kept in `stripe_events.error` (the NFR "alerts on failed webhooks").

## Architecture

```text
payment/controller/StripeWebhookController  --raw body + header-->  payment/services/StripeWebhookService (impl not transactional)
  1. event = verifier.verify(payload, header)                                  // 400 when invalid
  2. stripeEvents.insertIfAbsent(event, payload)   (own tx; already processed -> return)
  3. switch type:
       payment_intent.* -> payments.applyPaymentIntent(gateway.retrievePaymentIntent(event.objectId()))
       refund.*         -> refunds.applyRefund(gateway.retrieveRefund(event.objectId()))      (phase 5)
  4. stripeEvents.markProcessed(id)   | on exception: stripeEvents.markFailed(id, message) (own tx), log ERROR, rethrow -> 500

PaymentServiceImpl.applyPaymentIntent(state)  @Transactional
  payment = payments.findByPaymentIntentId(state.id())          // none -> WARN, return
  booking = bookings.findByIdForUpdate(payment.bookingId())     // booking row first, as everywhere
  payment.apply(state, now)                                     // terminal SUCCEEDED/CANCELED never change
  SUCCEEDED && booking PENDING && amounts match -> setStatusReason("Paid by card"); booking.confirm(now); publish BookingConfirmed
  SUCCEEDED otherwise                                        -> log ERROR (manual refund), booking untouched

payment/expiry/PaymentHoldExpiryJob  @Scheduled(cron = "${app.booking.expiry-cron}")
  paymentService.cancelForExpiredHolds()   // query without locks, then one Stripe cancel + applyPaymentIntent per payment
booking/expiry/BookingHoldExpiryJob (unchanged)  -> expireHolds(): JPQL now adds
  "and not exists (select 1 from Payment p where p.bookingId = b.id and p.status in :open)"
```

The two jobs share a cron and need no ordering. If the booking job runs first, it skips the booking, and the next
tick expires it once its intent is canceled (at most one minute late). New invariant, for C-MYBK-03 and every
future change: before a Pending booking leaves Pending for any reason other than payment, its open intent is canceled
on Stripe.

`BookingConfirmed(bookingId)` is a plain record event published with `ApplicationEventPublisher` inside the
transaction, by every confirm path: `applyPaymentIntent` and phase 2's zero-total start (wire that one here). Phase
4 adds its `AFTER_COMMIT` listener (emails). Here it only exists.

## Related Code Files

- Create: `payment/controller/StripeWebhookController.java`, `payment/services/StripeWebhookService.java`,
  `payment/services/impl/StripeWebhookServiceImpl.java`, `payment/expiry/PaymentHoldExpiryJob.java`,
  `booking/model/BookingConfirmed.java` (event record)
- Modify: `payment/services/PaymentService` + impl (`applyPaymentIntent`, `cancelForExpiredHolds`)
- Modify: `payment/repository/StripeEventRepository*` (`insertIfAbsent`, `markProcessed`, `markFailed`)
- Modify: `booking/repository/PaymentRepository*` (`findByPaymentIntentId`, `findOpenOfExpiredHolds(now, limit)`)
- Modify: `booking/repository/BookingRepositoryCustom` + impl (`findByIdForUpdate`; `findExpiredHoldsForUpdate` gains
  the `not exists` clause)
- Modify: `common/config/SecurityConfig.java`: `permitAll` for `POST /api/payments/stripe/webhook`, and the
  bearer-token resolver ignores `Authorization` there (like `PUBLIC_AUTH_ENDPOINTS`)
- Create (test): `payment/controller/StripeWebhookControllerTest.java`,
  `payment/services/impl/PaymentHoldExpiryTest.java`; adjust `booking/services/impl/BookingHoldExpiryTest.java` if
  it needs a booking with an open payment

## Implementation Steps

1. Controller: `@PostMapping(consumes = "application/json")` with `@RequestBody String payload`, so Spring's
   `StringHttpMessageConverter` keeps the exact bytes the signature covers, and `@RequestHeader(value =
   "Stripe-Signature", required = false)`.
2. Event log in its own short transactions (`REQUIRES_NEW` or separate repository calls without an outer
   transaction). Then a failure in step 3 still leaves the error on the event row.
3. `applyPaymentIntent` as above. Compare amount and currency through `StripeAmounts.fromStripe`. FAILED keeps the
   newest message. PROCESSING has no customer-visible change.
4. `cancelForExpiredHolds()`:
   - Read up to 100 open payments of Pending bookings with `hold_expires_at < now`, oldest hold first, with no
     locks.
   - For each one, in a try/catch: `applyPaymentIntent(gateway.cancelPaymentIntent(piId))`.
   - Return how many were handled. The job logs a count and the errors, like `BookingHoldExpiryJob`.
5. `findExpiredHoldsForUpdate` adds the `not exists` clause on `Payment`, which is in `booking/model`, so `booking/`
   still doesn't depend on `payment/`. Bookings that never started paying expire as before.
6. Security: add the webhook matcher next to the public routes, with a comment on why it is public (Stripe signs it;
   no token).
7. Update the javadocs that say "payment comes with C-PAY-01" (`BookingService`, `Booking`, `BookingController`,
   `BookingStatus`).

## Success Criteria

- [ ] Signature:
  - a tampered body, a wrong secret or a missing header → 400, with nothing stored and nothing changed
  - a valid one → 200
- [ ] `payment_intent.succeeded` for a started payment:
  - the booking is Confirmed with `confirmed_at`
  - one `booking_status_history` row PENDING → CONFIRMED, reason "Paid by card", `changed_by` null (system)
  - the payment is SUCCEEDED with `paid_at`
  - the payment GET shows it
- [ ] The same event delivered twice → 200 both times, one history row, and `processed_at` set once.
- [ ] Decline then success:
  - after `payment_failed` (the fake declined it with "Your card was declined."), the booking is Pending and the GET
    shows FAILED with that message and `payBy`
  - after a start, the same client secret comes back
  - after a later `succeeded` event, the booking is Confirmed
- [ ] Out of order: a `payment_failed` event delivered after success leaves SUCCEEDED and Confirmed.
- [ ] Success reported for an Expired booking: the booking stays Expired and the payment is SUCCEEDED (record +
  alert).
- [ ] The fake's retrieve throws → 500 with `stripe_events.error` set. The same event again → 200, processed.
- [ ] Expiry:
  - a Pending booking past its hold with an open payment isn't expired by `expireHolds()`
  - after `cancelForExpiredHolds()` the payment is CANCELED and `expireHolds()` expires it and releases its rooms
  - when the fake's cancel answers succeeded, the booking is Confirmed and not expired
  - when the fake's cancel throws, the booking stays Pending and held
- [ ] The existing `BookingHoldExpiryTest` and `BookingConcurrencyTest` still pass.

## Risk Assessment

- Raw body: if any filter or advice re-serializes the body, every signature fails. The test signs the exact bytes
  it sends, which catches that.
- Shared test database: `cancelForExpiredHolds()` acts on every test's expired holds with open payments, like
  `BookingHoldExpiryTest`. Tests assert only on their own bookings, and no other test leaves an open payment on a
  past hold.
- A Stripe outage keeps the affected rooms held until Stripe answers again. That is visible as ERROR logs every
  minute, and is better than releasing rooms that might still be paid.
- One batch of 100 cancels per minute, which is plenty for an MVP. Noted in README.
