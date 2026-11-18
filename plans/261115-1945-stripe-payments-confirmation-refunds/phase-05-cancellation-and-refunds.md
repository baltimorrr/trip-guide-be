---
phase: 5
title: Cancellation and refunds
status: completed
priority: P1
dependencies:
  - 4
---

# Phase 5: Cancellation and refunds

## Overview

The customer cancels a whole Confirmed booking (C-MYBK-03) and gets the policy's refund on their card through the
Stripe Refund API (C-PAY-05). The refund's status (pending, succeeded, failed) shows on the booking.

## Requirements

- Functional:
  - `GET /api/bookings/{reference}/cancellation` → `CancellationQuote`: `paid`, `penalty`, `refund` (Money), and
    `asOf` (the moment they were computed).
  - `POST /api/bookings/{reference}/cancel` takes `{expectedRefund: Money, reason?: string ≤ 500}` and returns 200
    with `BookingResponse`:
    - status Cancelled
    - `cancellation` = `{cancelledAt, reason, penalty, refund: {amount, status} | null}`
  - Refusals, for both calls:
    - another user's booking or an unknown reference → 404
    - Pending → 409 "An unpaid booking can't be cancelled. Its rooms are released when the hold ends"
    - Checked-in, Checked-out or No-show → 409 "This booking can no longer be cancelled"
    - Cancelled or Expired → 409 "This booking is already {status}"
    - a refund that isn't `expectedRefund` any more → 409 "The refund is now X. Check it and confirm", and nothing is
      written
  - Penalty, per active room, at `now`:
    - a non-refundable policy costs the room's whole stay
    - otherwise the penalty of the `CancellationSchedule` tier in effect, from the stored terms, the room's stored
      night prices, and the hotel's check-in time and zone; 0 when no tier is in effect
    - the booking's penalty is the sum, capped at `subtotal_minor`
  - **Refund (user decision):** `refund = round_half_up(paid × (subtotal − penalty) ÷ subtotal)`, clamped to
    `[0, paid]`, where `paid` = the succeeded payment's amount, or 0 for a zero-total booking.
  - Cancelling:
    - the booking becomes Cancelled, with `cancelled_at`, `cancelled_by` = the user, `cancel_reason` and
      `cancellation_fee_minor` = the penalty
    - its rooms become CANCELLED and go back on sale; the discount code use stays counted
    - history: the reason "Cancelled by customer[: reason]", by the user (`app.changed_by`)
    - refund > 0 → a PENDING `refunds` row (`initiated_by` = the user); refund 0 → no row and no Stripe call
  - After commit:
    - the refund goes to Stripe with key `refund-{reference}` and metadata `refund_id`
    - emails: `sendCancellation` to the booker and `notifyOwnerOfCancellation` to the partner contact
  - Refund webhooks `refund.created`, `refund.updated` and `refund.failed` re-read the refund and mirror its
    status. FAILED logs an ERROR (admin retry is A-PAY-01, out of scope).
  - A retry job (`app.payment.refund-retry-cron`, every minute):
    - resends PENDING rows with no Stripe id that are older than 1 minute
    - rows older than 23 h → FAILED + ERROR "refund it by hand", because Stripe forgets idempotency keys after 24 h
- Non-functional:
  - The Stripe call never runs inside the cancelling transaction, so a refund is never issued for a cancellation
    that rolled back.
  - Two concurrent cancels → one 200 and one 409.

## Architecture

```text
booking/controller/BookingController   GET /{reference}/cancellation, POST /{reference}/cancel
booking/services/BookingCancellationService -> impl/BookingCancellationServiceImpl   (BookingServiceImpl is already large)
  quote(userId, reference)                     readOnly
  cancel(userId, reference, request)           @Transactional
    booking = findByReferenceAndUserIdForUpdate                 // booking row, then inventory: the expiry's order
    check status; charge = CancellationCharges.of(booking, hotel, payment, now); compare expectedRefund
    setChangedBy(userId); setStatusReason(...)
    roomInventory.release(List.of(id))                          // BEFORE the rooms turn CANCELLED: release reads ACTIVE rooms
    booking.cancel(now, userId, reason, charge.penaltyMinor())  // rooms -> CANCELLED
    refund > 0 -> refunds.saveAndFlush(Refund.create(payment, booking, refund, currency, "Cancelled by customer", userId))
    publish BookingCancelled(bookingId, refundId|null)
booking/model/CancellationCharges   pure: per-room penalty (CancellationSchedule) + proportional refund (BigDecimal)

payment/refund/RefundSendListener  @TransactionalEventListener(AFTER_COMMIT) BookingCancelled -> refundService.send(refundId)
payment/refund/RefundRetryJob      @Scheduled(cron = "${app.payment.refund-retry-cron}") -> refundService.retryUnsent()
payment/services/RefundService -> impl/RefundServiceImpl
  send(refundId):      gateway.createRefund(...) outside a tx; then (tx) refund.markSent(state.id, state.status)
                       Stripe error -> WARN, left for the job
  applyRefund(state):  find by stripe id, else by metadata refund_id -> mirror status   (called by the webhook, phase 3 switch)
booking/notification/BookingMailListener  + on(BookingCancelled): customer + owner cancellation mails
```

## Related Code Files

- Create: `booking/services/BookingCancellationService.java`, `booking/services/impl/BookingCancellationServiceImpl.java`,
  `booking/model/{CancellationCharges,BookingCancelled}.java`,
  `booking/dto/{CancellationQuote,CancelBookingRequest,RefundDetails}.java`
- Modify: `booking/controller/BookingController.java`, `booking/model/Booking.java` (`cancel(...)`),
  `booking/model/BookingRoom.java` (`cancel()`), `booking/dto/BookingCancellation.java` (fields),
  `booking/mapper/BookingMapper.java` (cancellation + refund), `booking/services/impl/BookingServiceImpl.java`
  (`find` loads the refund), `booking/repository/BookingRepositoryCustom` + impl (`setChangedBy`),
  `booking/repository/RefundRepository*` (`findUnsentOlderThan`, `findByStripeRefundId`, `findFirstByBookingId`)
- Modify: `booking/notification/BookingMailListener.java`, `booking/services/BookingMailService` + impl (cancellation
  mails)
- Create: `payment/services/RefundService.java`, `payment/services/impl/RefundServiceImpl.java`,
  `payment/refund/{RefundSendListener,RefundRetryJob}.java`
- Modify: `payment/services/impl/StripeWebhookServiceImpl.java` (`refund.*` branch), `application.yml`
  (`app.payment.refund-retry-cron: ${APP_REFUND_RETRY_CRON:0 * * * * *}`), `application-test.yml` (`"-"`), a
  `PaymentProperties` record (or reuse `StripeProperties`) for the cron
- Create (test): `booking/model/CancellationChargesTest.java` (unit), `booking/controller/BookingControllerCancelTest.java`,
  `payment/services/impl/RefundServiceTest.java`, refund cases in `StripeWebhookControllerTest`

## Implementation Steps

1. `CancellationCharges` unit-first:
   - free → full refund; a non-refundable room → refund 0
   - a 50% tier with a discount and tax on top → the proportional amount, rounded half up
   - mixed rooms (one free, one penalised)
   - penalty > subtotal → refund 0; a zero-total booking → 0
   - the time boundary exactly at a tier start
2. Domain methods:
   - `Booking.cancel(now, userId, reason, penaltyMinor)`: only from CONFIRMED; the CHECK needs `cancelled_at`; it
     cancels every room
   - `BookingRoom.cancel()`
3. `setChangedBy(userId)` mirrors `setStatusReason` (`set_config('app.changed_by', :id, true)`; V10's trigger casts
   it to bigint).
4. The service and controller as above. Preview and cancel share `CancellationCharges`, so they can't disagree at the
   same instant.
5. `RefundServiceImpl`:
   - `send` reads the refund and its payment's intent id
   - `retryUnsent` handles up to 100 rows per tick, oldest first; `for update skip locked` isn't needed, since
     Stripe's idempotency key makes two sends of one row return the same refund
   - `applyRefund` mirrors Stripe's status and logs ERROR on FAILED
6. Webhook: add the `refund.*` branch to the dispatcher. Register `refund.created`, `refund.updated` and
   `refund.failed` on the Stripe endpoint (documented in phase 6).
7. Mail listener: `on(BookingCancelled)` sends both mails with the cancellation block filled.

## Success Criteria

- [ ] `CancellationChargesTest` covers every case in step 1.
- [ ] Preview of a Confirmed booking (confirmed through the phase 3 webhook helper):
  - a free policy → refund = paid
  - with check-in soon enough that a penalty tier applies (fixture policy rules) → the expected penalty and the
    proportional refund
- [ ] Cancel with the preview's refund:
  - 200 Cancelled, and one history row CONFIRMED → CANCELLED with `changed_by` = the user and the reason
  - the hotel page's `roomsLeft` is back up
  - the refund row is SUCCEEDED after the fake answers, and the fake shows one refund for `refund-{reference}`
  - `GET /api/bookings/{reference}` shows the cancellation and the refund status
  - the recorder holds the customer and owner cancellation mails
- [ ] A stale `expectedRefund` → 409, and the booking, rooms and refunds are unchanged.
- [ ] Pending, Cancelled, Expired and Checked-in (walked there through `JdbcClient` and the triggers) → 409; another
  user → 404.
- [ ] A non-refundable rate → refund 0, no refund row, no Stripe call, and the booking is still Cancelled.
- [ ] The fake throws on refund create → the row stays PENDING with no Stripe id. `retryUnsent()` (its `created_at`
  set 2 min back) sends it. A row 24 h old → FAILED.
- [ ] A `refund.failed` webhook → the booking GET shows the refund FAILED.
- [ ] Two concurrent cancels of one booking → exactly one 200, and one refund row.

## Risk Assessment

- **Double refund:** only one refund row per booking is possible (Confirmed → Cancelled happens once), and every
  send uses `refund-{reference}`. Past Stripe's 24 h key window the job stops instead of resending.
- **Penalty uses the hotel's current check-in time and zone, not the time of booking.** A later hotel edit moves the
  tiers. This is accepted, because night prices and rules are stored. Noted in README limitations.
- **Lock order:** booking row → room_inventory, as in the expiry. Checkout never locks an existing booking row, so
  there's no cycle.
- **Hibernate auto-flush before the native `release`:** calling `release` before mutating the entity keeps the order
  explicit, whatever the flush mode.
