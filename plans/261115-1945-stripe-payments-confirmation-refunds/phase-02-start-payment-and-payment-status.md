---
phase: 2
title: Start payment and payment status
status: completed
priority: P2
dependencies:
  - 1
---

# Phase 2: Start payment and payment status

## Overview

The customer's two payment calls: start (or resume) paying a Pending booking, which returns the PaymentIntent's
client secret for Stripe's Payment Element, and read the payment's state, which the client polls after Stripe.js
returns and which carries the decline message and the retry deadline (C-PAY-01, C-PAY-02).

## Requirements

- Functional:
  - `POST /api/bookings/{reference}/payment` → 200 `PaymentSession`:
    - `reference`, `status` (booking), `amount` (Money)
    - `clientSecret` (null when nothing is left to pay)
    - `paymentStatus`
    - `payBy` = the hold's end
  - `GET /api/bookings/{reference}/payment` → 200 `BookingPayment`:
    - `reference`, `bookingStatus`
    - `payBy` (set only while Pending)
    - `amount`
    - `payment`: `{status, failureReason, paidAt}`, or null before the first start
  - A booking that isn't the caller's, or doesn't exist → 404 (the same answer, so references can't be probed).
  - POST refusals:
    - not Pending → 409 "This booking is already paid" (Confirmed) or "This booking is {status} and can't be paid"
    - hold passed → 409 "The rooms are no longer held for this booking. Book them again"
    - an open intent Stripe says is no longer payable (processing or succeeded) → 409 "This payment is being
      processed. Check its status in a moment"
  - Total 0 → the booking is confirmed at once (reason "Nothing to pay"), `clientSecret` null, no Stripe call.
- Non-functional:
  - Never confirms a paid booking itself: only Stripe's state does (phase 3).
  - The GET reads the database only, with no Stripe call per poll.
  - The POST spends from the `CHECKOUT` rate limit.

## Architecture

```text
payment/controller/BookingPaymentController  (/api/bookings/{reference}/payment)
  -> payment/services/PaymentService (interface)  -> impl/PaymentServiceImpl
       start(userId, reference):  @Transactional
         booking = bookings.findByReferenceAndUserIdForUpdate(...)   // row lock: one intent per booking
         checks (status, hold, total 0 -> booking.confirm(now))
         open = payments.findOpenByBookingId(id)
         open present -> gateway.retrievePaymentIntent(open.piId); payable? return its client secret : 409
         else         -> state = gateway.createPaymentIntent(reference, total, currency)   // key payment-{reference}
                         payments.saveAndFlush(Payment.create(...))
       status(userId, reference): @Transactional(readOnly = true)
  -> payment/mapper/PaymentMapper, payment/dto/{PaymentSession, BookingPayment},
     booking/dto/PaymentDetails (phase 4's booking GET reuses it; booking/ never imports payment/)
```

The Stripe create call runs inside the transaction on purpose. The booking row lock stops two starts from
creating two intents. If the transaction then rolls back, the next start sends the same idempotency key and gets
the same intent back; an intent nobody holds a client secret for can't be charged.

## Related Code Files

- Create: `payment/controller/BookingPaymentController.java`, `payment/services/PaymentService.java`,
  `payment/services/impl/PaymentServiceImpl.java`, `payment/mapper/PaymentMapper.java`,
  `payment/dto/{PaymentSession,BookingPayment}.java`, `booking/dto/PaymentDetails.java`
- Modify: `booking/repository/BookingRepository.java` (`findByReferenceAndUserId`),
  `BookingRepositoryCustom` + impl (`findByReferenceAndUserIdForUpdate`, `PESSIMISTIC_WRITE`),
  `booking/repository/PaymentRepository*` (`findOpenByBookingId`, `findFirstByBookingIdOrderByIdDesc`)
- Modify: `booking/model/Booking.java` (`confirm(Instant now)`: only from PENDING, sets `confirmedAt`)
- Modify: `common/ratelimit/RateLimitPolicy.java` (`CHECKOUT` also matches `POST /api/bookings/*/payment`) and the
  comment in `application.yml`
- Create (test): `payment/controller/BookingPaymentControllerTest.java`; extend `booking/controller/BookingClient`
  with `startPayment(reference)` / `payment(reference)` helpers

## Implementation Steps

1. `Booking.confirm(now)` throws `IllegalStateException` unless PENDING. Before it, the service calls
   `bookings.setStatusReason(...)`, as `expireHolds` does.
2. Repository lookups by reference + user. Every booking write that locks the booking row uses the same order:
   booking row first, then the rows that hang off it (payments, refunds, inventory).
3. `PaymentServiceImpl.start` as above:
   - Read "payable" from the retrieved intent: REQUIRES_PAYMENT or FAILED.
   - A FAILED intent is reused as is. Stripe already moved it back to "needs a payment method", so the same client
     secret retries.
4. Controller: `@AuthenticationPrincipal Jwt`, the user id from `sub` like `BookingController`. Javadoc lists every
   status code, as the other controllers do.
5. Rate limit: add the matcher and extend `common/ratelimit/RateLimitPolicyTest` with the new path.

## Success Criteria

- [ ] Start → 200 with the fake's client secret.
  - The payment row is REQUIRES_PAYMENT, with the booking's `total_minor` and currency and the intent id.
  - A second start returns the same secret, and the fake shows one create for that reference.
- [ ] Two concurrent starts of one booking create one intent and one row (the row lock, and the
  `one_open_payment` index behind it).
- [ ] 404 for another user's booking and for an unknown reference.
- [ ] 409 for an Expired booking, a hold set in the past (through `JdbcClient`, like `BookingHoldExpiryTest`), and
  a booking already Confirmed.
- [ ] A 100% discount code → the booking is Confirmed at once, `clientSecret` is null, the history reason is
  "Nothing to pay", and there is no Stripe call.
- [ ] The fake gateway fails → 503 problem+json, and nothing is written.
- [ ] GET before any start shows `payment` null and `payBy` = the hold. After a start it shows REQUIRES_PAYMENT.

## Risk Assessment

- Holding the booking lock during a Stripe call: only that one row, only for the customer's own start, and the
  expiry job skips locked rows. Acceptable.
- A total of 0 bypassing Stripe is a money path: it's covered by a test and logged with its own status reason.
