---
phase: 3
title: Booking creation with room hold
status: completed
priority: P1
dependencies:
  - 2
---

# Phase 3: Booking creation with room hold

## Overview

`POST /api/bookings` turns one cart group into a Pending booking. Under the cart lock it prices again. It refuses a
changed total until the customer confirms it, holds the rooms with one guarded upsert into `room_inventory`, and
writes the booking with its rooms, nights, taxes, guest details and code use. Then it deletes the group's cart lines.

## Requirements

- Functional (C-BOOK-01, 04, 05, 06):
  - Request `BookingRequest`:
    - `cartItemIds`, `discountCode?`
    - `expectedTotal {amount, currency}` (the quote's `total`)
    - `booker {fullName, email, phone, countryCode}`, all required. They are validated with `@NotBlank`/`@Size`,
      `@Email`, `@PhoneNumber` and `@IsoCountry`.
    - `guestName?` (≤100), `specialRequests?` (≤1000), `estimatedArrival?` ("HH:mm")
    - `leadGuests?: [{cartItemId, names: [≤100 chars]}]`
    - `acceptedTerms` (`@AssertTrue`)
  - Response 201 `BookingResponse`:
    - `reference`, `status`, `holdExpiresAt`
    - the hotel and the stay
    - the booker, `guestName`, `specialRequests`, `estimatedArrival`
    - `rooms[] {roomTypeName, ratePlanName, boardType, adults, childAges, leadGuestName, price, cancellation}`
    - the same `PriceBreakdown` as the quote
  - Errors:
    - 400: validation; lead guests for an item not in the request, or more names than rooms; lines of more than
      one group; a refused code
    - 404: an unknown item, or a second submit after the lines are gone
    - 409: a changed total; a line can't be booked; the rooms were just taken; a tax can't be calculated
- Non-functional:
  - One transaction; nothing written on any error.
  - Lock order: cart, then code, then inventory.
  - The last room is never sold twice.

## Architecture

**`BookingServiceImpl.create(userId, request)`**, `@Transactional`:
1. `carts.findByUserIdForUpdate(userId)`, or 404 "Cart item"; then the lead guest checks.
2. The phase 2 pricing core (group check, rates, rooms left).
3. If `discountCode` is set, `discounts.reserve(...)` locks the code row and checks it, including the limits and
   "first booking only".
4. `TaxCalculator`, then the total. If it differs from `Money.toMinor(expectedTotal.amount, currency)`, or the
   currency differs → 409 "The total is now %s. Check the new price and confirm it".
5. `roomInventory.hold(roomsByRoomType, checkIn, checkOut)`. On `false` → 409 "The last rooms of <room> were just
   booked. Go back to your cart to see what is left".
6. `Booking.create(...)`:
   - status PENDING, a reference from `BookingReference.next()` (checked with `existsByReference`, retried)
   - `holdExpiresAt = now + properties.hold()`
   - the booker email normalized like `User.normalizeEmail`; `termsAcceptedAt = now`
   - `commissionRate` from `hotels.findCommissionRate(hotelId)`
   - per line × quantity, a `BookingRoom` with its nights (from `RoomRateView.nightPricesMinor`), its lead guest
     name and a `CancellationTerms` snapshot from `RateOfferView` and the rules
   - a `BookingTax` per tax line

   Then `bookings.saveAndFlush`; the insert trigger logs PENDING with the user as `changed_by`.
7. If a code was used, `DiscountRedemption.create(code, booking, userId, amount)`.
8. `cartItems.deleteAllInBatch(groupItems)`, then build the response with `BookingMapper`.

**`RoomInventoryRepository`**: a plain interface plus a `@Repository` impl, like `DestinationSyncRepository`. The
table has no entity: only set-based SQL writes it.

```sql
-- Native: JPQL has no insert ... on conflict do update with a guard. Rows are written in key order, so two
-- checkouts lock them in the same order and can't deadlock; a guarded update that doesn't fit writes no row.
insert into room_inventory as i (room_type_id, stay_date, total_rooms, booked_rooms, updated_at)
select rt.id, n.stay_date, rt.total_rooms, r.rooms, now()
from unnest(cast(:roomTypeIds as bigint[]), cast(:rooms as int[])) as r(room_type_id, rooms)
join room_types rt on rt.id = r.room_type_id
cross join generate_series(cast(:checkIn as date), cast(:checkOut as date) - 1, interval '1 day') as n(stay_date)
where r.rooms <= rt.total_rooms
order by rt.id, n.stay_date
on conflict (room_type_id, stay_date) do update
    set booked_rooms = i.booked_rooms + excluded.booked_rooms, updated_at = now()
    where not i.is_closed and i.booked_rooms + excluded.booked_rooms <= i.total_rooms
```

`hold` returns `affectedRows == roomTypes × nights`. Hibernate expands a list inside `array[...]` into a row value
(CLAUDE.md), so the arrays are bound as `Long[]`/`Integer[]` parameters. If that binding fails, fall back to one
`values` row per room type rendered with numbered parameters. The `release(bookingIds)` twin is written in phase 4.

**`HotelRepositoryCustom.findCommissionRate(hotelId)`** is native (`select p.commission_rate from hotels h join
partners p on p.id = h.partner_id`), because `partners` has no entity until the partner stories, with a comment
saying so. If a read-only mapping turns out simpler at implementation, use it instead.

**`booking/config/BookingProperties`** is `@Validated @ConfigurationProperties("app.booking")
record(@NotNull Duration hold, @NotBlank String expiryCron)`. `application.yml` gets `hold: 15m` and
`expiry-cron: ${APP_BOOKING_EXPIRY_CRON:0 * * * * *}`; the test profile sets `expiry-cron: "-"`.

## Related Code Files

- Create:
  - `booking/dto/{BookingRequest,BookerRequest,LeadGuests,BookingResponse,BookedRoom}.java`
  - `booking/model/BookingReference.java`, `booking/config/BookingProperties.java`
  - `booking/repository/{RoomInventoryRepository,impl/RoomInventoryRepositoryImpl}.java`
  - tests: `booking/controller/BookingControllerTest.java`, `booking/services/impl/BookingConcurrencyTest.java`,
    `booking/repository/RoomInventoryRepositoryTest.java`, `booking/model/BookingReferenceTest.java`
- Modify:
  - `booking/services/BookingService.java`, `booking/services/impl/BookingServiceImpl.java`,
    `booking/controller/BookingController.java`, `booking/mapper/BookingMapper.java`
  - `booking/model/Booking.java` (factory), `discount/services/*` (redemption)
  - `hotel/repository/HotelRepositoryCustom.java`, `hotel/repository/impl/HotelRepositoryImpl.java`
  - `application.yml`, `src/test/resources/application-test.yml`

## Implementation Steps

1. Write `RoomInventoryRepository.hold` and `RoomInventoryRepositoryTest`:
   - it creates missing rows from `total_rooms`
   - it adds to existing rows
   - it refuses past `total_rooms` and on a closed night, and writes nothing then (rollback in the test's
     transaction)
   - several room types in one call
2. Write `BookingReference` and its test: the alphabet, the length and the `HB` prefix.
3. Add `BookingProperties` and the config.
4. Write `create`, the DTOs and the mapper, and expose `POST /api/bookings` (201).
5. Write `BookingControllerTest`:
   - **Happy path**:
     - status `PENDING`, the reference, and `holdExpiresAt` ≈ now + 15m
     - one room row per room, with its lead names; nights summing to the room price; tax rows
     - the booker, guest name, special requests, arrival `"18:30"` and `terms_accepted_at`, checked in the
       database
     - `total_minor = subtotal − discount + tax`
     - a history row PENDING with `changed_by` = the user
   - **Cart and availability**:
     - the cart keeps another group and loses the booked one
     - while pending, the hotel page's `roomsLeft` for those dates drops by the rooms booked
   - **Changed totals**:
     - the price changed after the quote (a `rate_plan_prices` override) → 409, with no booking row, no inventory
       change and the cart lines kept; quote again and send the new total → 201
     - a total that doesn't match the code → 409
   - **Validation**:
     - `acceptedTerms` false or missing → 400
     - more lead names than rooms → 400; a lead name for an item not in the request → 400
     - a missing booker phone → 400; a bad country → 400
   - **Discounts**: a code with `usage_limit_total = 1`, used by the first booking, makes the second fail with "used
     up"; the redemption row holds the amount
   - **Duplicates and auth**: a second submit of the same lines → 404; without a token → 401
6. Write `BookingConcurrencyTest`:
   - two users each have the room type's last room (`total_rooms = 1`) in their carts
   - both call `BookingService.create` on two threads released by a `CountDownLatch`
   - exactly one booking exists and the other call throws `BusinessException` with status 409
   - `booked_rooms = 1`

   The test runs the service, not MockMvc, to get two real transactions.
7. Run the cart tests again, because pending holds change the rooms left.

## Success Criteria

- [x] A booking is written only when the total was confirmed and the rooms fit; any error leaves the database
  unchanged.
- [x] The concurrency test passes on repeated runs (`-Dsurefire.rerunFailingTestsCount` not used; run it five times
  by hand).
- [x] The search, hotel page and cart tests stay green.

## Risk Assessment

- **Inventory rows outlive the bookings that created them.** The hold copies `room_types.total_rooms` into a new
  row. If an owner later changes the room type's total, rows created by holds keep the old number. That is how
  `room_inventory` already works (a row overrides the room type); O-ROOM-03's calendar edits rows. Listed in Known
  limitations.
- **Shared database in tests.** Each test makes its own hotel and room types, so holds never touch another test's
  rooms. The concurrency test needs committed data, which the fixtures already commit.
- **Binding `bigint[]`/`int[]` parameters** in a native query with Hibernate 7. The fallback is described in
  Architecture.
