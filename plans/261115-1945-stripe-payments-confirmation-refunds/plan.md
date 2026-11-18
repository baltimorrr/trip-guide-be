---
title: 'Stripe card payment, confirmation and refunds (C-PAY-01/02/03/05, C-MYBK-03)'
description: >-
  Pay a Pending booking by card through a Stripe PaymentIntent (Payment Element
  on the client, 3DS by Stripe.js). The signed webhook alone confirms it; a
  declined card shows Stripe's message and retries on the same intent until the
  hold ends, and the hold expiry cancels the intent before releasing the rooms.
  A booking GET and logged confirmation emails (customer + owner) give proof of
  the reservation. Customers cancel a whole Confirmed booking and get a
  proportional refund through the Stripe Refund API, with its status on the
  booking. New V11 payments, refunds and stripe_events tables.
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - payments
  - stripe
blockedBy: []
blocks: []
created: 2026-11-15T00:00:00.000Z
---

# Stripe card payment, confirmation and refunds (C-PAY-01/02/03/05, C-MYBK-03)

## Overview

Sources: `spring-base/USER_STORIES.md` §6 C-PAY-01/02/03/05, §7 C-MYBK-03 and the booking lifecycle, §10 NFRs
(webhooks verified by signature, PCI scope minimal, minor units, alerts on failed webhooks).
`spring-base/DATABASE-SCHEMA.md` `payments`, `refunds`, `stripe_events`, `bookings`. Builds on the completed checkout
plan `261114-1131-checkout-bookings-hold-discounts` (Pending booking + 15-min hold + expiry job). Every other plan in
`spring-base/plans/` is completed; the pending frontend plan (`/home/jazzy/Personal/java/plans/261115-1917-trip-guide-frontend-base`)
builds no story pages, so nothing blocks this plan and it blocks nothing.

| Endpoint | Auth | Stories |
|---|---|---|
| `POST /api/bookings/{reference}/payment` | owner of the booking | 01 start or resume card payment (client secret) |
| `GET /api/bookings/{reference}/payment` | owner | 01 poll for confirmation, 02 failure message + retry deadline |
| `POST /api/payments/stripe/webhook` | public, Stripe signature | 01 confirm, 02 failure, 05 refund status |
| `GET /api/bookings/{reference}` | owner | 03 confirmation page, 05 refund status |
| `GET /api/bookings/{reference}/cancellation` | owner | MYBK-03 penalty and refund preview |
| `POST /api/bookings/{reference}/cancel` | owner | MYBK-03 cancel, 05 refund |
| Jobs (every minute) | — | 02 cancel open payments of expired holds; 05 retry refunds that didn't reach Stripe |

## User Decisions (2026-11-15, do not reverse silently)

| Decision | Choice |
|---|---|
| Scope | C-PAY-01, 02, 03, 05 **and C-MYBK-03** (the only trigger for refunds). C-PAY-04/06, C-MYBK-01/02/04, O-BOOK-04 stay out |
| Stripe integration | **PaymentIntent + Payment Element.** The API returns a client secret; card data and 3DS stay in Stripe.js. No Checkout Session (its 30-min minimum lifetime can't match the 15-min hold) |
| Late payment | A success reported for a booking that is no longer Pending (unreachable by design) is **recorded and logged as ERROR** for a manual refund in the Stripe dashboard. Not auto-refunded |
| Cancellation | **Whole booking only**, Confirmed bookings only (answers USER_STORIES' open question "one room or whole booking?" for now) |
| Refund math | **Proportional:** `refund = paid × (subtotal − penalty) ÷ subtotal`, rounded half up once, clamped to `[0, paid]`. Non-refundable → 0, free → full |
| Git | No staging or commits by Claude (standing preference) |

## Planner Defaults (changeable)

- **The webhook re-reads the object from Stripe** (`retrieve` the PaymentIntent or Refund named in the event) and
  applies its current state. Repeated or out-of-order events are harmless, and event parsing doesn't depend on the
  webhook endpoint's API version. `stripe_events` logs every event once (its id is the key) with `processed_at` or
  `error`.
- **One PaymentIntent per booking**, created under the booking row lock with idempotency key `payment-{reference}`
  (references are random, so a reset local DB never reuses a key). `POST .../payment` reuses the open one. A DB
  backstop index allows one open payment per booking.
- **Cancel before expire.** A job cancels the open PaymentIntent of every Pending booking whose hold passed; the
  booking expiry skips bookings that still have an open payment. A canceled intent can't charge, so rooms are only
  released once Stripe can no longer take money. If Stripe answers "already succeeded", the booking is confirmed.
- **Confirmation only from Stripe's state** (webhook or the cancel answer), never from the browser. A total of 0
  (100% code) is confirmed when payment starts, without Stripe.
- **`booking/` owns the `payments` and `refunds` entities** (its expiry and cancellation read them); `payment/`
  holds everything Stripe (gateway, webhook, jobs) and depends on `booking/`, never the reverse.
- **Emails are only logged** (like the auth emails), sent after the transaction commits via
  `@TransactionalEventListener(AFTER_COMMIT)`. The owner notice goes to `partners.contact_email` (no owner accounts
  yet).
- **Refunds:** the row is written with the cancellation; the Stripe call happens after commit with idempotency key
  `refund-{reference}`. A job resends rows that didn't reach Stripe; after 23 h (Stripe keeps keys 24 h) it marks
  them FAILED and logs ERROR instead of risking a second refund.
- **Stripe keys are optional locally:** without `APP_STRIPE_SECRET_KEY` the payment endpoints answer 503 "Card
  payments aren't set up on this server"; the rest of the app runs. Tests use a fake gateway and a fake webhook
  secret.
- Cards only for now (`allowedPaymentMethodTypes = [card]`; stripe-java 34 removed `paymentMethodTypes`).
- A cancelled paid booking keeps its discount code use; a Pending booking can't be cancelled (it expires).

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Stripe gateway and payment schema](./phase-01-stripe-gateway-and-payment-schema.md) | Completed |
| 2 | [Start payment and payment status](./phase-02-start-payment-and-payment-status.md) | Completed |
| 3 | [Webhook confirmation and hold expiry](./phase-03-webhook-confirmation-and-hold-expiry.md) | Completed |
| 4 | [Booking page and confirmation emails](./phase-04-booking-page-and-confirmation-emails.md) | Completed |
| 5 | [Cancellation and refunds](./phase-05-cancellation-and-refunds.md) | Completed |
| 6 | [Docs and final verification](./phase-06-docs-and-final-verification.md) | Completed |

Phases run in order; each ends with its own tests green.

## Dependencies

- Builds on the completed `261114-1131-checkout-bookings-hold-discounts`. No blocking relationships.
- New dependency: `com.stripe:stripe-java` **34.1.0** (latest stable, 2026-10-29; pins API `2026-10-28.endive`).
- Smoke run (phase 6) needs Stripe test-mode keys and the Stripe CLI (not installed on this machine). Without them
  the smoke run is skipped and reported; `./mvnw verify` doesn't need them.

## Acceptance Criteria

- [x] Starting payment returns a client secret for exactly one PaymentIntent of the booking's total and currency;
  repeating it returns the same intent. Expired, confirmed or someone else's bookings are refused (409/409/404).
- [x] Only a signed webhook (invalid signature → 400) confirms: the booking becomes Confirmed with `confirmed_at`,
  the change is logged by the system with its reason, and the payment is Succeeded. The same event twice changes
  nothing more.
- [x] A declined card leaves the booking Pending; the payment GET shows Stripe's message and the hold deadline, and a
  later success on the same intent confirms it.
- [x] After the hold, the open intent is canceled first, then the booking expires and its rooms are released; an
  intent that already succeeded confirms the booking instead.
- [x] `GET /api/bookings/{reference}` shows the booking with hotel address and contact, rooms, guests, board,
  cancellation terms, price breakdown, payment, and (once cancelled) penalty and refund status.
- [x] Confirmation emails (customer + hotel's partner contact) are logged after commit with every field C-PAY-03
  lists; cancellation emails likewise.
- [x] Cancel preview shows penalty and refund now; cancel with a stale `expectedRefund` → 409 and nothing written;
  cancel → Cancelled (logged as the customer), rooms back on sale, refund created on Stripe and its status tracked
  by webhook. Non-refundable → refund 0, no Stripe call.
- [x] `./mvnw verify` is green; README, CLAUDE.md, DATABASE-SCHEMA.md and USER_STORIES.md describe the result.

## Not in Scope

- Checkout Session, wallets and other methods (C-PAY-06), receipts and invoices (C-PAY-04)
- the booking list, resend confirmation (C-MYBK-01/04); C-MYBK-02 beyond the GET built here
- owner cancellation (O-BOOK-04), admin payment views and refund retry (A-PAY-01), real email delivery and
  `email_outbox`
- cancelling one room of a booking, cancelling a Pending booking, auto-refunding late payments

## Rollback

Before phase 1, archive `spring-base` (without `target/`) to the session scratchpad as
`rollback/spring-base-before-c-pay.tgz`. Nothing is committed by Claude. V11 is new, so rolling back before it runs
anywhere shared is deleting the files; after that, add a V12 instead of editing V11.

## Implementation Notes (2026-11-16)

All six phases are done and verified. Nothing is staged or committed.

**Tests.** `./mvnw verify`: 430 tests, 0 failures (373 before, plus 57).

**Local smoke run without Stripe keys** (port 8081, demo seed; script `payment-smoke-no-stripe.sh` in the
scratchpad).
- The app starts without keys and logs that card payments answer 503.
- Checkout booked `HBHQB8PNC3` (3,168,000 VND). Its page showed the hotel's formatted address.
- `POST .../payment` and the webhook answered 503 with their messages. `GET .../payment` showed `payBy`, and the
  cancellation preview of the Pending booking answered 409.
- The smoke account `smoke-pay-1794763631@example.com` and its booking stay in the local DB; the booking expires
  the next time the app runs.
- The local database already had V11 (applied 2026-11-15 20:26 by another run of the app against it, with the same
  checksum).

**Stripe test-mode smoke run: skipped.** There are no Stripe test keys in `.env` (not read; `.env.example` only now
lists them), and the Stripe CLI isn't installed. Real-Stripe checks stay to do: success, decline, 3D Secure,
expiry cancel, refund.

**Deviations from the phase files.**
- `PaymentGatewayException` extends `BusinessException` (503), so `GlobalExceptionHandler` is unchanged and
  `common/` imports no feature package.
- The expiry query's "no open payment" clause and `BookingConfirmed` landed in phase 2 with `PaymentServiceImpl`,
  not in phase 3.
- The webhook reads the event's id, type and object id with Jackson 3 after `Webhook.Signature.verifyHeader`, so
  stripe-java's Gson event model isn't used.
- Hotel data for bookings comes from a new `HotelContactView` (JPQL, listed or not), mapped into the hotel page's
  `HotelAddress`. `HotelMapper.toCancellation` is the room-terms builder shared with the hotel page; `toRate` now
  calls it.
- `BookingResponses` (package-private) builds every booking response from the stored rows. `POST /api/bookings` uses
  it too, and the old `PricedGroup`-based `toBooking` is gone; `BookingControllerTest` passes unchanged.
- `BookingResponse.hotel` is now `BookingHotel`, a superset of `QuoteHotel`, plus `confirmedAt`, `payment` and
  `cancellation`. These are additive changes to the POST response.
- Emails for both events go through one `BookingMailListener`, which reads with `BookingService.notice` in
  `REQUIRES_NEW`. Refunds go out through `payment/refund/RefundSendListener` and `RefundRetryJob`.
- The refund retry cron is the yml property `app.payment.refund-retry-cron`, read by `@Scheduled` only; there is no
  properties record.
- `booking_rooms.cancelled_at` is now mapped, and a cancel sets it per room.

**Not done (ask first).** No row in USER_STORIES' "Changes in this version" table, and the "Last updated" header is
unchanged: both are the user's changelog.

**Rollback.** In the scratchpad: `rollback/spring-base-before-c-pay.tgz` and `rollback/USER_STORIES-before-c-pay-docs.md`.
