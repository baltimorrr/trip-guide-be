---
phase: 1
title: Stripe gateway and payment schema
status: completed
priority: P2
dependencies: []
---

# Phase 1: Stripe gateway and payment schema

## Overview

Adds stripe-java, the V11 tables (`payments`, `refunds`, `stripe_events`), their entities, the outgoing Stripe
gateway behind an interface, the webhook signature verifier, and the test doubles every later phase uses. No
endpoint yet.

## Requirements

- Functional: amounts sent to Stripe are in Stripe's minor units for the booking's currency; keys are optional
  locally (503 when missing); a webhook signature is verified with the real stripe-java code, also in tests.
- Non-functional: the secret key and webhook secret never appear in logs or `toString()`; no Stripe network call
  in tests.

## Architecture

```text
booking/model      Payment, PaymentStatus, Refund, RefundStatus        (booking owns the rows; expiry/cancel read them)
booking/repository PaymentRepository(+Custom/impl), RefundRepository(+Custom/impl)
payment/config     StripeProperties (app.stripe.secret-key, webhook-secret; masked toString)
payment/model      StripeEvent (natural key = Stripe event id), PaymentIntentState, RefundState, WebhookEvent (records),
                   StripeAmounts (ISO minor units <-> Stripe amounts)
payment/repository StripeEventRepository (+impl: HQL insert ... on conflict do nothing)
payment/services   PaymentGateway (interface)  -> impl/StripePaymentGateway (StripeClient)
                   StripeWebhookVerifier (interface) -> impl/StripeSignatureVerifier (Webhook.constructEvent)
test support       FakePaymentGateway (+ @TestConfiguration Config, @Primary), StripeWebhooks.sign(payload)
```

`PaymentGateway` (outgoing calls only, so tests replace it while signature checks stay real):

```java
PaymentIntentState createPaymentIntent(String bookingReference, long amountMinor, String currency); // key payment-{ref}
PaymentIntentState retrievePaymentIntent(String id);
PaymentIntentState cancelPaymentIntent(String id);   // not cancelable (already succeeded/canceled) -> retrieve and return state
RefundState createRefund(String paymentIntentId, long amountMinor, String currency, String bookingReference, long refundId); // key refund-{ref}
RefundState retrieveRefund(String id);
```

`PaymentIntentState(id, status, amountMinor, currency, clientSecret, failureMessage, bookingReference)` maps Stripe's
status: `requires_payment_method` → FAILED when `last_payment_error` is set, else REQUIRES_PAYMENT;
`requires_confirmation`/`requires_action` → REQUIRES_PAYMENT; `processing`/`requires_capture` → PROCESSING;
`succeeded` → SUCCEEDED; `canceled` → CANCELED. Amounts come back through `StripeAmounts.fromStripe`.
`RefundState(id, status, refundId from metadata)`: `pending`/`requires_action` → PENDING, `succeeded` → SUCCEEDED,
`failed`/`canceled` → FAILED.

Errors: `StripeException` (network, API, auth) → new `PaymentGatewayException` → `GlobalExceptionHandler` 503
"Card payment is unavailable right now. Try again in a moment" (log at ERROR with Stripe's request id). Blank secret
key → `BusinessException(503, "Card payments aren't set up on this server")`. Stripe's `amount_too_small` on create →
`BusinessException(409, "The total is below the smallest amount Stripe can charge")`.

## Related Code Files

- Modify: `pom.xml` (`com.stripe:stripe-java:34.1.0`, property `stripe.version`)
- Create: `src/main/resources/db/migration/V11__create_payment_tables.sql`
- Create: `booking/model/{Payment,PaymentStatus,Refund,RefundStatus}.java`,
  `booking/repository/{PaymentRepository,PaymentRepositoryCustom,RefundRepository,RefundRepositoryCustom}.java` + `impl/`
- Modify: `booking/model/Booking.java` (map `confirmed_at`, `cancelled_at`, `cancelled_by`, `cancel_reason`,
  `cancellation_fee_minor`; domain methods come in phases 3 and 5)
- Create: `payment/config/StripeProperties.java`, `payment/model/*`, `payment/repository/*`, `payment/services/*`
- Modify: `common/exception/GlobalExceptionHandler.java` (503 for `PaymentGatewayException`)
- Modify: `application.yml` (`app.stripe.*` from `APP_STRIPE_SECRET_KEY` / `APP_STRIPE_WEBHOOK_SECRET`, default
  empty), `src/test/resources/application-test.yml` (fake `sk_test_only…` / `whsec_test_only…`), `.env.example`
- Create (test): `support/FakePaymentGateway.java`, `support/StripeWebhooks.java`; modify `support/IntegrationTest.java`
  to import `FakePaymentGateway.Config` (one shared context, no `@MockitoBean`)
- Create (test): `payment/model/StripeAmountsTest.java`, `payment/services/impl/StripeSignatureVerifierTest.java`,
  `payment/services/impl/StripePaymentGatewayTest.java` (blank key → 503, no network)

## Implementation Steps

1. Archive `spring-base` (without `target/`) to the scratchpad: `rollback/spring-base-before-c-pay.tgz`.
2. Add stripe-java 34.1.0. Before coding against it, check the builder names in its sources:
   `StripeClient.builder()`, `client.v1().paymentIntents().create(params, RequestOptions)`,
   `PaymentIntentCreateParams.builder().addAllowedPaymentMethodType(...)` (34.0.0 removed `paymentMethodTypes`),
   `Webhook.constructEvent`. Adjust names if they differ; don't downgrade.
3. V11 (conventions of V10: identity `bigint` ids, `version`, `created_at`/`updated_at`, enum columns as `varchar` +
   CHECK of Java names, currency `varchar(3)` + regex CHECK):
   ```sql
   create table payments (
       id bigint generated by default as identity primary key,
       booking_id bigint not null references bookings (id),
       provider varchar(20) not null default 'stripe',
       stripe_payment_intent_id varchar(255) unique,
       stripe_checkout_session_id varchar(255) unique, -- schema doc column; the PaymentIntent integration leaves it null
       amount_minor bigint not null check (amount_minor >= 0),
       currency varchar(3) not null check (currency ~ '^[A-Z]{3}$'),
       status varchar(20) not null default 'REQUIRES_PAYMENT'
           check (status in ('REQUIRES_PAYMENT', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELED')),
       failure_reason varchar(500),
       paid_at timestamptz,
       version bigint not null default 0, created_at timestamptz not null, updated_at timestamptz not null,
       check (status <> 'SUCCEEDED' or paid_at is not null)
   );
   create unique index one_successful_payment on payments (booking_id) where status = 'SUCCEEDED';
   -- Not in the schema doc: at most one payment a customer can still complete per booking.
   create unique index one_open_payment on payments (booking_id) where status in ('REQUIRES_PAYMENT', 'PROCESSING', 'FAILED');
   create index payments_booking_id_idx on payments (booking_id);

   create table refunds (
       id bigint generated by default as identity primary key,
       payment_id bigint not null references payments (id),
       booking_id bigint not null references bookings (id),
       stripe_refund_id varchar(255) unique,
       amount_minor bigint not null check (amount_minor > 0),
       currency varchar(3) not null check (currency ~ '^[A-Z]{3}$'),
       reason varchar(500),
       status varchar(10) not null default 'PENDING' check (status in ('PENDING', 'SUCCEEDED', 'FAILED')),
       initiated_by bigint references users (id), -- null = the system
       version bigint not null default 0, created_at timestamptz not null, updated_at timestamptz not null
   );
   create index refunds_booking_id_idx on refunds (booking_id);
   -- Not in the schema doc: refunds still to send to Stripe (the retry job).
   create index refunds_unsent on refunds (created_at) where stripe_refund_id is null and status = 'PENDING';

   -- Written once per Stripe event, never versioned (like booking_status_history).
   create table stripe_events (
       id varchar(255) primary key, -- evt_…: the idempotency key
       type varchar(100) not null,
       payload jsonb not null,
       received_at timestamptz not null default now(),
       processed_at timestamptz,
       error varchar(1000)
   );
   ```
4. Entities: `Payment.create(bookingId, paymentIntentId, amountMinor, currency)` (REQUIRES_PAYMENT) and
   `apply(PaymentIntentState, Instant now)` that ignores changes once SUCCEEDED or CANCELED and sets `paid_at` on
   success; `Refund.create(...)`, `markSent(stripeRefundId, status)`, `apply(RefundState)`, `fail()`.
   `StripeEvent` maps `payload` with `@JdbcTypeCode(SqlTypes.JSON)` as `String`; no `BaseEntity` (like
   `WishlistEntry`).
5. `StripeAmounts.toStripe(minor, currency)` / `fromStripe(amount, currency)`: ISK and UGX ×100 / ÷100 (ISO has 0
   digits, Stripe wants two); three-decimal currencies (BHD, JOD, KWD, OMR, TND) must end in 0, otherwise
   `BusinessException(409, "Card payment in {currency} needs an amount in tens of its smallest unit")`; everything
   else 1:1. Unit test each case plus VND and USD.
6. `StripePaymentGateway`: one `StripeClient` built from the key (max 2 network retries), currency lowercased,
   metadata `booking_reference` (and `refund_id` on refunds), description `Booking {reference}`.
7. `StripeSignatureVerifier.verify(payload, signatureHeader)` → `WebhookEvent(id, type, objectId)`; the object id is
   read from `data.object.id` of the raw JSON, so it doesn't depend on the event's API version. Bad or missing
   signature → `BusinessException(400, "Invalid Stripe signature")`; blank secret → 503.
8. Test doubles: `FakePaymentGateway` keeps intents and refunds in maps (ids `pi_fake_…`, `re_fake_…`), counts
   creates per reference, and lets tests `succeed(id)`, `decline(id, message)`, `failNextCall()`; `cancel` of a
   succeeded intent returns SUCCEEDED. `StripeWebhooks.sign(payload)` builds `t=…,v1=HMAC-SHA256(secret, t.payload)`
   with the test secret, plus `event(id, type, objectId)` JSON builders.

## Success Criteria

- [ ] App starts with and without Stripe keys; V11 applies and `ddl-auto: validate` passes.
- [ ] `StripeAmountsTest`, `StripeSignatureVerifierTest` (valid, tampered body, wrong secret, old timestamp) and
  `StripePaymentGatewayTest` pass; existing suite still green.
- [ ] `StripeProperties.toString()` hides both secrets.

## Risk Assessment

- stripe-java API surface drift (34.x): verify names from the jar before writing the gateway (step 2).
- Wrong minor units overcharge or undercharge by 100×: `StripeAmounts` is the only conversion, unit-tested.
- Storing Stripe payloads keeps billing details of failed attempts (no card numbers); noted in README limitations.
