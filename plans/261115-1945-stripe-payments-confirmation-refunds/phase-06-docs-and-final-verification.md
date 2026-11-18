---
phase: 6
title: Docs and final verification
status: completed
priority: P2
dependencies:
  - 5
---

# Phase 6: Docs and final verification

## Overview

Bring the four project docs in line with what was built, run the full suite, and smoke-test against Stripe test
mode when keys and the Stripe CLI are available.

## Requirements

- Functional: docs describe the endpoints, env vars, Stripe setup, rules and limits as built (read each doc before
  editing it; verify dates and claims against the code).
- Non-functional: `./mvnw verify` green; no secrets in docs, logs or the plan; nothing staged or committed.

## Related Code Files

- Modify: `README.md`:
  - the endpoint table, with the 6 endpoints and the webhook
  - the env var table: `APP_STRIPE_SECRET_KEY`, `APP_STRIPE_WEBHOOK_SECRET`, `APP_REFUND_RETRY_CRON`
  - a "Stripe setup" section
  - "Known limitations"
- Modify: `CLAUDE.md`:
  - the "Implemented so far" sentence, and a **Decided (2026-11-15), payments** paragraph
  - payment rules for maintainers, under Persistence / Hotel catalog
  - the Tests notes: the fake gateway, the webhook signing helper, the mail recorder, and the expiry tests acting on
    every test's holds
- Modify: `DATABASE-SCHEMA.md`: `payments`, `refunds` and `stripe_events` as built (bigint ids, version, the extra
  indexes `one_open_payment` and `refunds_unsent`, `payments` CHECK on `paid_at`)
- Modify: `USER_STORIES.md`:
  - status marks for C-PAY-01/02/03/05 and C-MYBK-03, with "Left" lines
  - the C-BOOK-04 sentence "Until payment exists (C-PAY-01), every booking expires…"
  - C-MYBK-03's "Decide" line, and the open question "one room or whole booking"
  - not the "Changes in this version" table or the "Last updated" header: those are the user's changelog, so ask
- Modify: `.env.example` (Stripe keys with how to get them), this plan's `plan.md` (implementation notes)

## Implementation Steps

1. README "Stripe setup":
   - Test-mode keys come from the Dashboard (Developers → API keys).
   - Locally: `stripe listen --forward-to localhost:${SERVER_PORT}/api/payments/stripe/webhook` prints the
     `whsec_…` secret.
   - In prod: a webhook endpoint with API version `2026-10-28.endive` and the events `payment_intent.succeeded`,
     `payment_intent.payment_failed`, `payment_intent.canceled`, `payment_intent.processing`, `refund.created`,
     `refund.updated` and `refund.failed`.
   - The client uses the publishable key with the Payment Element, and polls `GET …/payment` after
     `confirmPayment`.
2. README "Known limitations" (append):
   - Emails are logged only; one sent after commit can be lost in a crash.
   - Cards only.
   - At most 100 expired-hold payment cancels per minute.
   - A Stripe outage keeps rooms held.
   - A late payment for a non-Pending booking needs a manual refund.
   - Failed refunds need a manual retry (A-PAY-01).
   - The penalty uses the hotel's current check-in time.
   - No one-room or Pending cancellation.
   - Three-decimal currencies need amounts in tens.
   - `stripe_events` keeps payloads (billing details of failed attempts, no card numbers) with no retention job.
   - The webhook shares the per-IP general rate limit; Stripe retries a 429.
3. CLAUDE.md rules to record:
   - Stripe's state is the only source of confirmation, and the webhook re-reads the object.
   - Lock order: the booking row first, then payments, refunds and inventory.
   - **Before a Pending booking leaves Pending for any reason other than payment, cancel its open PaymentIntent.**
   - `booking/` owns the `payments` and `refunds` rows; `payment/` depends on `booking/`, never the reverse.
   - `StripeAmounts` is the only minor-unit conversion.
   - Mail and Stripe calls run after commit (`AFTER_COMMIT` listeners with `REQUIRES_NEW` reads).
   - Idempotency keys: `payment-{reference}` and `refund-{reference}`.
4. Run `./mvnw verify` with Docker running. Record the test count before and after.
5. Smoke run, only when `APP_STRIPE_SECRET_KEY` (test mode, `sk_test_…`) is set and the `stripe` CLI is installed.
   Otherwise report "smoke skipped" and why. Use port 8081 / DB 5434 (this machine) and the demo seed:
   - book a cart group, start payment, then `stripe payment_intents confirm <pi> --payment-method pm_card_visa`
     → the booking is Confirmed via `stripe listen`, and the confirmation mail is logged
   - `pm_card_chargeDeclined` → FAILED with Stripe's message, a retry with `pm_card_visa` → Confirmed
   - an expired hold with an open intent → the intent is canceled on Stripe, then the booking expires and
     `roomsLeft` is restored
   - cancel a free-cancellation booking → the refund is SUCCEEDED in Stripe and on the booking GET
   - 3DS (`pm_card_authenticationRequired`) needs a browser with the Payment Element. Check that it reaches
     `requires_action` and stays Pending, and leave the full challenge to the frontend.
   - Write the smoke script to the scratchpad, never into the repo.
6. Whole-plan consistency check: re-read `plan.md` and every phase file for stale names, and write "Implementation
   Notes" (deviations, additions, test counts, smoke results, rollback archive path) in `plan.md`.

## Success Criteria

- [ ] `./mvnw verify` is green, with the before and after test counts recorded.
- [ ] The README endpoint and env tables match the controllers and `application.yml`. The Stripe setup steps work
  as written in the smoke run, or the run is reported as skipped.
- [ ] CLAUDE.md, DATABASE-SCHEMA.md and USER_STORIES.md claims match the code, with 15 Nov 2026 dates for the
  decisions.
- [ ] `git status` shows only the plan's files; nothing is staged.

## Risk Assessment

- Docs drift from the code: every claim is checked against the code before the phase closes (step 6).
- Smoke data stays in the local database: name the smoke account in the notes, as the checkout plan did.
