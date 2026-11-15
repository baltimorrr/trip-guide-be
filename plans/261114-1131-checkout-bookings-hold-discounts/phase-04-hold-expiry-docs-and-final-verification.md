---
phase: 4
title: Hold expiry docs and final verification
status: completed
priority: P2
dependencies:
  - 3
---

# Phase 4: Hold expiry, docs and final verification

## Overview

This phase covers:
- a scheduled job that expires Pending bookings whose hold has passed, releasing their rooms and their code use
  (C-BOOK-04 "the hold is released if payment is not completed")
- a demo discount code
- the four docs
- the full suite and a local smoke run

## Requirements

- Functional:
  - Every minute (`app.booking.expiry-cron`), every PENDING booking with `hold_expires_at < now` becomes EXPIRED.
  - Its rooms are subtracted from `room_inventory.booked_rooms` for each night, and its `discount_redemptions` row
    is deleted.
  - History gets a row with `changed_by` null and the reason "Payment not completed before the hold expired".
- Non-functional:
  - Safe next to a later payment confirmation: rows are locked with `skip locked`, and only PENDING rows change.
  - Batches of 100, repeated until none are left.

## Architecture

- `BookingRepositoryCustom.findExpiredHoldsForUpdate(Instant now, int limit)`: JPQL
  `select b from Booking b where b.status = PENDING and b.holdExpiresAt < :now order by b.holdExpiresAt`, with
  `PESSIMISTIC_WRITE` and the lock timeout hint `-2` (SKIP LOCKED in Hibernate's PostgreSQL dialect).
- `BookingRepositoryCustom.setStatusReason(String reason)`: native `select set_config('app.status_reason', :reason,
  true)`, transaction-local and read by the log trigger. It's native because JPQL can't select without an entity.
- `RoomInventoryRepository.release(Collection<Long> bookingIds)`: one native update, the twin of `hold`.

  ```sql
  update room_inventory i set booked_rooms = i.booked_rooms - r.rooms, updated_at = now()
  from (select br.room_type_id, n.stay_date, count(*) as rooms
        from booking_rooms br join booking_room_nights n on n.booking_room_id = br.id
        where br.booking_id in (:bookingIds) and br.status = 'ACTIVE'
        group by br.room_type_id, n.stay_date) r
  where i.room_type_id = r.room_type_id and i.stay_date = r.stay_date
  ```
- `Booking.expire()` throws `IllegalStateException` unless the booking is PENDING; it sets the status to EXPIRED.
- `DiscountRedemptionRepository.deleteByBookingIdIn(ids)` is a derived delete, or a JPQL bulk delete in the custom
  impl.
- `BookingServiceImpl.expireHolds()` runs `@Transactional` per batch through a small loop in the job, so a long
  backlog doesn't hold one huge transaction. It returns the count.
- `booking/expiry/BookingHoldExpiryJob` is a package-private `@Component`, like `CartExpiryJob`:
  `@Scheduled(cron = "${app.booking.expiry-cron}")`. It logs "Expired {} unpaid bookings and released their rooms"
  and catches `RuntimeException`.

## Related Code Files

- Create:
  - `booking/expiry/BookingHoldExpiryJob.java`
  - `booking/repository/BookingRepositoryCustom.java`, `booking/repository/impl/BookingRepositoryImpl.java`
  - test `booking/services/impl/BookingHoldExpiryTest.java`
- Modify:
  - `booking/model/Booking.java`, `booking/services/*`, `booking/repository/RoomInventoryRepository*.java`,
    `discount/repository/DiscountRedemptionRepository.java`
  - `src/main/resources/db/seed/R__demo_catalog.sql` (a `welcome10` code: 10%, platform-funded, all hotels, no end
    date within a year, guarded by `not exists` like the other seed rows)
  - docs: `README.md`, `CLAUDE.md`, `DATABASE-SCHEMA.md`, `USER_STORIES.md`

## Implementation Steps

1. Write `release`, `expire()`, `findExpiredHoldsForUpdate`, `setStatusReason`, `expireHolds()` and the job.
2. Write `BookingHoldExpiryTest`:
   - book through the API with a code
   - move `hold_expires_at` into the past with JDBC (only this booking)
   - call `expireHolds()`
   - check: status EXPIRED; `booked_rooms` back to what it was before; the redemption is gone, so the code's limit is
     free again; a history row PENDING → EXPIRED with the reason and null `changed_by`
   - a second booking whose hold hasn't passed is untouched; a second run expires nothing more

   Like the cart's expiry test, this one expires every test's past holds, so only past-dated fixtures may be left
   for it.
3. Add the demo code to the seed.
4. Update the docs. Read each one before editing it and keep the user's own lines.
   - **README**:
     - the intro sentence
     - the endpoint rows for both POSTs
     - the `checkout` rate-limit row
     - a "Bookings and checkout" section with curl (quote → book, with the 409 "confirm the new total" loop)
     - the test-table rows and the fixtures paragraph (`booking`, `discountCode`, `BookingClient`)
     - the structure entries for `booking/`, `discount/` and V10
     - config rows: `APP_BOOKING_EXPIRY_CRON`, `app.booking.hold`, `app.rate-limit.limits.checkout`
     - the template step
     - Known limitations:
       - no payment yet, so every booking expires
       - no display-currency total
       - fixed taxes only in the hotel's currency
       - commission isn't computed yet
       - hold rows copy `total_rooms`
       - no booking GET until C-MYBK
       - codes are created by SQL
   - **CLAUDE.md**:
     - the implemented list
     - "Decided (2026-11-14), bookings": account required, the hotel's currency, lines removed at booking, the hold
       starting at booking creation
     - Persistence bullets: the triggers and the settings they read; `room_inventory` writes (hold and release, the
       lock order); `booking_room_nights` as an element collection; the `jsonb` snapshot
     - the tax calculator as the only place tax amounts are computed
     - test fixture notes; `BookingHoldExpiryTest` expiring every past hold
   - **DATABASE-SCHEMA.md**: the header comment (bigint ids aside, list what was edited); the `discount_codes` CHECK
     on money limits; `booking_status_history` without update columns; the triggers' settings
     (`app.changed_by`/`app.status_reason`)
   - **USER_STORIES.md**:
     - C-BOOK-01 ✅, 03 ✅, 04 ✅, 05 ✅ and 06 ✅, each with a short "Decided 14 Nov 2026" note
     - C-BOOK-02 🟡, with "Left: the total in the display currency (C-SRCH-05)"
     - C-CART-01 → ✅, with its "Left" bullet replaced
     - move the two answered open questions (sign-in, charge currency) to **Decided**
     - do not add a "Changes in this version" row without asking the user (it is their changelog)
5. Run `./mvnw verify` and record the test count against the one before (324).
6. Smoke test on the local app (8081/5434), with a script in the scratchpad like the cart smoke:
   - sign up and add two rooms; quote with `welcome10` → VAT on the discounted price
   - book → 201 and Pending
   - the hotel page's `roomsLeft` drops
   - change a night price, then book a fresh cart line with the old total → 409
   - move `hold_expires_at` back → the job tick logs it, and `roomsLeft` comes back
   - clean up the demo price rows
7. Run `ck plan check` per phase; add implementation notes to `plan.md`; tick the acceptance criteria.

## Success Criteria

- [x] An expired hold gives back exactly the rooms it took and its code use, and is logged once.
- [x] The full suite is green, the smoke run passes, and the docs match the code.

## Risk Assessment

- **The expiry job versus a later payment webhook.** Both lock the booking row. The expiry only touches PENDING
  rows it locked with `skip locked`; the webhook (C-PAY-01) must lock and check for PENDING too. Noted in CLAUDE.md
  for that plan.
- **`set_config` and Hibernate's flush order.** The native select flushes pending changes before it runs, so it is
  called first in the transaction, before any entity changes.
