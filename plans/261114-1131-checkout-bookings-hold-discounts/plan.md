---
title: >-
  Checkout and bookings: quote, room hold, discounts, guest details
  (C-BOOK-01..06)
description: >-
  Checkout of one cart group (one hotel, one date range) into one Pending
  booking. A quote shows the price breakdown (lines, discount code, taxes by
  type, total) and the booker details pre-filled from the profile. Creating the
  booking prices again, asks the customer to confirm a changed total, holds the
  rooms for 15 minutes under row locks (the last room can't be sold twice),
  stores the guest details, a lead guest per room, special requests and the
  arrival time, and removes the lines from the cart. A job expires unpaid holds
  and releases their rooms. New V10 bookings and discount tables, with the
  status rules in the database. Stripe (C-PAY-01/02) is the next plan.
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - booking
  - checkout
blockedBy: []
blocks: []
created: 2026-11-14T00:00:00.000Z
---

# Checkout and bookings: quote, room hold, discounts, guest details (C-BOOK-01..06)

## Overview

Sources: `spring-base/USER_STORIES.md` §5 C-BOOK-01..06, plus what C-CART-01 left for checkout (reject a checkout
with rooms of several hotels or dates), §6 C-PAY-01/02 (Pending until paid, Expired when the hold runs out) and the
§7 booking status lifecycle. `spring-base/DATABASE-SCHEMA.md` `bookings`, `booking_rooms`, `booking_room_nights`,
`booking_taxes`, `booking_status_history`, `discount_codes`, `discount_code_hotels`, `discount_redemptions`,
`tax_rules`. Build on both docs as they are now (they hold the user's 13 Nov edits); never revert them.

Every other plan in `plans/` is completed. This one builds on the completed cart plan
(`261113-1245-cart-add-live-prices-edit-expiry`), so nothing blocks it and it blocks nothing.

| Endpoint (signed-in, any role) | Stories |
|---|---|
| `POST /api/bookings/quote` | 02 breakdown, 03 code check, 05 booker pre-fill, 01 one-group check |
| `POST /api/bookings` | 01 booking with room lines, 04 re-check, confirm a new total, hold, 05 guest details and terms, 06 arrival time |
| `BookingHoldExpiryJob` (every minute) | 04 the hold is released when payment isn't completed |

## User Decisions (2026-11-14, do not reverse silently)

| Decision | Choice |
|----------|--------|
| Scope | **Hold**: C-BOOK-01..06 as written. A booking ends **Pending** with a 15-minute hold. Stripe (C-PAY-01/02) confirms it in the next plan, so until then every booking expires. No fake "confirm" endpoint |
| Sign-in | **Account required.** `bookings.user_id not null`, and checkout starts from the server cart. This answers the open question "Can guests book without signing in" |
| Charge currency | **The hotel's currency** (`bookings.currency`). The total in the customer's display currency (`display_currency`, `display_total_minor`, `fx_rate` stay null) waits for exchange rates (C-SRCH-05), so C-BOOK-02 stays 🟡. This answers the open question "Which currency is the card charged in" |
| Cart lines | **Removed** in the transaction that creates the Pending booking; the cart's other groups stay |
| Git | No commits or staging by Claude (standing preference) |

## Planner Defaults (changeable)

- **Checkout input = cart item ids of one group**: the same hotel, check-in and check-out. Otherwise the answer is
  400 "A booking holds the rooms of one hotel for one date range". An id that isn't in the user's cart is 404. Every
  line must be bookable by the cart's rules (the same `findRoomRates` call per distinct dates and guests per room; a
  group's lines of one room type share `roomsLeft`). Otherwise the answer is 409:
  - check-in passed
  - the rate no longer sells the stay, or the room doesn't fit the guests
  - "Only N rooms of this type are left"
- **The hold starts when the booking is created** (the guest form is submitted and the customer continues to
  payment), not at the quote. The schema needs booker details and accepted terms on every booking row, and the quote
  writes nothing. The hold lasts `app.booking.hold` (15m).
- **Confirming a new total (C-BOOK-04).** The booking request carries `expectedTotal`, the quote's `total`. When the
  fresh total differs, up or down, the answer is 409 "The total is now X. Check the new price and confirm it" and
  nothing is written. The client asks for a new quote and sends again.
- **Room hold.** One native upsert into `room_inventory` adds the rooms to `booked_rooms` for every (room type,
  night), in `(room_type_id, stay_date)` order:
  - Only where `booked + n <= total` and the night isn't closed.
  - A missing row is created from `room_types.total_rooms`.
  - Fewer rows written than room types × nights → 409 and rollback.

  Row locks plus the existing CHECK `booked_rooms <= total_rooms` mean the last room never goes to two bookings.
  `booked_rooms` already counts pending and confirmed bookings, so search, the hotel page and the cart stop offering
  held rooms with no change to their SQL.
- **Lock order in every booking write**: the cart row, then the discount code row, then inventory rows (by room
  type and date). Checkouts can't deadlock each other.
- **Status rules live in the database** (the schema doc's two triggers):
  - On insert, only `PENDING` is allowed.
  - An update that changes the status must follow a lifecycle arrow.
  - Every insert, and every update that changes the status, writes `booking_status_history`. `changed_by` is the
    `app.changed_by` setting, else the booking's user on insert, else null (system). `reason` is the
    `app.status_reason` setting.
  - Hibernate writes every column on update, so both triggers compare `OLD.status` with `NEW.status`.
- **Price breakdown (C-BOOK-02)**, all in the hotel's currency, minor units:
  - A line's subtotal is its rooms' stay price (the sum of `night_price()` over the nights) × quantity, and the
    booking's `subtotal` is the sum of its lines.
  - At most one code per booking. Its discount is off the subtotal and never more than it.
  - Taxes are the active rules of the hotel's country that cover at least one night, in rule id order, rounded half up
    per rule. Each rule applies only to the nights inside its validity.
    - `PERCENT` on top = value % of the nights' room price. With `applies_after_discount` (the default), that price
      is reduced in proportion to the discount.
    - `PERCENT` included = the part of that price already inside it: `price × v / (100 + v)`.
    - `PER_NIGHT` = value × rooms × nights.
    - `PER_PERSON_NIGHT` = value × guests (adults and children) × nights.
    - `PER_BOOKING` = value once.
  - Included taxes are listed with their amount but not added. `total = subtotal − discount + taxes on top`
    (`fee_minor` 0).
  - A fixed-amount tax in another currency than the hotel's → 409 "Taxes for this hotel can't be calculated yet".
    There is no conversion, and the platform must never under-charge tax.
- **Discount codes (C-BOOK-03).**
  - Codes are stored lowercased (the project's citext rule), and the typed code is matched trimmed and in any case.
  - Checks, each with its own message, in this order:
    - unknown, inactive or deleted → "This discount code is not valid"
    - not started yet; ended
    - not for this hotel
    - in another currency
    - minimum spend not met
    - used up (in total)
    - already used by this user
    - "for a first booking only"
  - A percent code gives `round(subtotal × v / 100)`, capped by `max_discount_minor`. A fixed code's value is in
    major units, converted with `Money.toMinor`.
  - Money limits (the fixed value, min spend and max discount) are in the code's `currency`, which must be the
    hotel's.
  - Uses are counted from `discount_redemptions`. An expired hold deletes its row, so it doesn't count.
  - "First booking only" means the user has no other booking that is pending, confirmed, checked in, checked out or
    no-show.
  - Codes are written by SQL until A-DISC-01 (admin). The demo seed adds `welcome10`.
- **Guest details (C-BOOK-05).**
  - `booker` (`fullName`, `email`, `phone`, `countryCode`) is required. The quote pre-fills it from the profile: the
    email always, the name, and the phone and country when saved.
  - `guestName` is set when booking for someone else; `specialRequests` holds at most 1000 characters, and the API
    docs say they are not guaranteed.
  - `acceptedTerms` must be true. It covers the terms, the privacy policy and the rates' cancellation policies shown
    in the quote, and is stored as `terms_accepted_at`.
- **Rooms (C-BOOK-01).** There is one `booking_rooms` row per room booked. A line's rooms share its guests per room,
  and each room can have a lead guest name: `leadGuests: [{cartItemId, names}]`, with at most `quantity` names.
- **Arrival time (C-BOOK-06).** `estimatedArrival` ("HH:mm") is optional and stored as `time`, mapped with
  `LocalTimeJdbcType` like `Hotel.checkInFrom`.
- **Snapshot at booking.** The booking keeps what was sold, so later edits don't change it:
  - per room: the room type name, the board code, the cancellation policy (name, refundable, rules) as `jsonb`, and
    each night's price (`booking_room_nights`)
  - per tax rule: its name, whether it was included, and its amount (`booking_taxes`)
  - the partner's `commission_rate`. `commission_minor` stays 0 until payouts (A-PAY-02) settle whether discounts
    reduce it (open question "Who funds discount codes").
- **Booking reference**: "HB" + 8 characters from a 31-character alphabet without 0/O/1/I/L, generated again in the
  rare case it already exists.
- **Rate limit**: a new `CHECKOUT` policy (20 per minute per client) covers both POSTs. Both run the priced query,
  and the quote is where codes can be guessed.
- **No GET of a booking yet**: the create response has everything, and the list and detail are C-MYBK-01/02. A
  double-submitted create waits on the cart lock and then gets 404, because its lines are gone.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Booking and discount schema](./phase-01-booking-and-discount-schema.md) | Completed |
| 2 | [Checkout quote with taxes and discounts](./phase-02-checkout-quote-with-taxes-and-discounts.md) | Completed |
| 3 | [Booking creation with room hold](./phase-03-booking-creation-with-room-hold.md) | Completed |
| 4 | [Hold expiry docs and final verification](./phase-04-hold-expiry-docs-and-final-verification.md) | Completed |

Each phase depends on the one before it.

## Dependencies

None open. The cart plan (`261113-1245-cart-add-live-prices-edit-expiry`) is completed.

## Acceptance Criteria

- [x] The quote of one group returns:
  - per line: nights, price per room, quantity and subtotal
  - the subtotal, the discount, and taxes by type (included or on top)
  - the total
  - the booker pre-filled from the profile, and each line's cancellation terms

  Its line prices equal `GET /api/cart`'s for the same lines.
- [x] Lines of two hotels or two date ranges → 400; unknown or someone else's items → 404; a line that is
  unavailable, past check-in or short of rooms → 409.
- [x] Each discount rule refuses with its own message. A valid code lowers the subtotal, and tax is on the
  discounted price unless the rule says otherwise.
- [x] A created booking has:
  - status Pending, a reference, and `hold_expires_at` = now + 15 minutes
  - one room row per room booked, with its guests and lead guest name, plus its nights and price
  - the tax rows, booker, guest name, special requests, arrival time and terms time
  - totals that satisfy the table's CHECK

  Its cart lines are gone and the cart's other groups remain.
- [x] A changed total → 409 with nothing written; sending again with the new quote's total → 201.
- [x] Two concurrent bookings of the last room: exactly one succeeds, the other gets 409, and `booked_rooms` never
  exceeds `total_rooms`.
- [x] While Pending, the held rooms leave the hotel page's room list and other carts (`roomsLeft` drops).
- [x] The expiry job turns Pending bookings past their hold into Expired, releases their rooms and their code use,
  and logs the change with its reason.
- [x] The database refuses a status change that isn't a lifecycle arrow, and logs every change.
- [x] `reviews.booking_id` references `bookings`, and the review and hotel tests still pass.
- [x] `./mvnw verify` is green; README, CLAUDE.md, DATABASE-SCHEMA.md and USER_STORIES.md describe the result.

## Not in Scope

- Stripe payment, confirmation and the confirmation email (C-PAY-01/02/03)
- the booking list, detail, cancellation and resend (C-MYBK-01..04)
- owner booking views and status changes (O-BOOK)
- discount and tax admin (A-DISC-01, A-CONF-02); currencies and exchange rates (C-SRCH-05); room numbers
  (`room_assignments`, O-BOOK-03)
- guest checkout and emails

## Rollback

Before phase 1, `spring-base` is archived (without `target/`) to the session scratchpad:
`rollback/spring-base-before-c-book.tgz`. Nothing is committed by Claude.

## Implementation Notes (2026-11-14)

All four phases are done and verified. Nothing is staged or committed.

**Tests.** `./mvnw verify` ran 373 tests with 0 failures (324 before, plus 49).

**Local smoke run.** `booking-smoke.sh` in the scratchpad ran against the demo seed on port 8081. V10 and the reseeded
`welcome10` applied cleanly.
- The quote with `WELCOME10`: 9,680,000 − 968,000 + VAT 871,200 = 9,583,200 VND.
- The booking: Pending, `HB32RFV5DN`, a 15-minute hold, lead guests on the right rooms, and the cart emptied.
- `roomsLeft` went 5 → 3 while held.
- A changed night price → 409 "The total is now 1650000 VND…".
- The job's tick expired the booking. It was logged as the system, with its reason; `roomsLeft` went back to 5 and
  the code use was released.
- The demo price row is deleted. The smoke account `smoke-book-1794674605@example.com` (and its one leftover cart
  line) stays in the local DB.

**Deviations from the phase files.**
- `RoomInventoryRepository.hold` is one upsert per room type, called in id order, instead of one statement with
  bound arrays. This avoids array binding in native SQL and keeps the lock order.
- `TaxCalculator` takes `TaxRuleView` (JPQL `select new`), not the entity, so it is unit-tested without entities.
- The booking service computes "first booking" and passes it in `DiscountUse`, so `discount/` never depends on
  `booking/`.
- `Money` gained `@NotNull` components for `@Valid expectedTotal`. Responses are unaffected.
- `QuoteHotel` has no `checkOutUntil`, because none of the views read it.
- The `jsonb` terms mapped with `@JdbcTypeCode(SqlTypes.JSON)` on the `CancellationTerms` record; the String fallback
  wasn't needed.
- The expiry lock renders `for no key update ... skip locked`, using `Timeouts.SKIP_LOCKED_MILLI` because
  `LockOptions` is deprecated.

**Additions.**
- `BookingRoomStatus`
- `BookingRepositoryCustom.setStatusReason`
- `CatalogFixtures.taxRule(country, name, calcType, ...)`
- `ReviewRepositoryTest` now uses real bookings, with a foreign-key test
- C-AUTH-06 marked ✅: its only "Left" was the checkout pre-fill.

**Not done (ask first).**
- No row in USER_STORIES' "Changes in this version" table.
- The "Last updated: 13 Nov 2026" header is unchanged. Both are the user's changelog.

**Rollback.** In the scratchpad: `rollback/spring-base-before-c-book.tgz`, and
`rollback/USER_STORIES-before-c-book-docs.md` (USER_STORIES before the status edits).
