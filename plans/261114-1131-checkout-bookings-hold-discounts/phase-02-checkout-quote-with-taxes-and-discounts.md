---
phase: 2
title: Checkout quote with taxes and discounts
status: completed
priority: P1
dependencies:
  - 1
---

# Phase 2: Checkout quote with taxes and discounts

## Overview

`POST /api/bookings/quote` prices one cart group the way the cart does and adds a discount code and tax amounts. It
returns the breakdown, the booker pre-filled from the profile and each line's cancellation terms. It writes nothing.
Phase 3 creates the booking from the same pricing.

## Requirements

- Functional (C-BOOK-01 group check, 02 breakdown, 03 code checks, 05 pre-fill):
  - Request: `{cartItemIds: [..1–20 ids], discountCode?}`
  - Response `CheckoutQuote`:
    - `hotel {id, slug, name, city, countryCode, checkInFrom, checkOutUntil}`, `checkIn`, `checkOut`, `nights`
    - `lines[]`: `cartItemId`, `roomTypeName`, `ratePlanName`, `boardType`, `quantity`, `adults`, `childAges`,
      `pricePerRoom`, `subtotal` and `cancellation` (`RateCancellation`, as on the hotel page)
    - `subtotal`; `discount {code, amount}` or null; `taxes[] {name, included, amount}`; `total`
    - `booker {fullName, email, phone, countryCode}`
  - Errors: 400 for lines of more than one group, or a refused code (with its message); 404 for an unknown or
    foreign item; 409 when a line can't be booked or a tax can't be calculated.
- Non-functional:
  - One `findRoomRates` call per distinct guests per room of the group, as in the cart.
  - Rate-limited by the new `CHECKOUT` policy.

## Architecture

**Pricing core** (private to `BookingServiceImpl`, reused by `create` in phase 3):
1. `cartItems.findInCart(cartId)`, keeping the requested ids. An id that is missing → `ResourceNotFoundException`
   ("Cart item").
2. Group check: one hotel and the same dates → otherwise 400.
3. Check-in before `SearchCriteria.today()` → 409 "Check-in for these rooms has passed". Then, per distinct
   `(adults, children)`: `hotels.findRoomRates(hotelId, new StayRequirement(checkIn, checkOut, 1, adults, children))`.
4. Per line, the rate row must exist and fit → otherwise 409 "<room> is no longer available for these dates". Rooms
   needed per room type, summed over the group's lines, must be ≤ `roomsLeft` → otherwise 409 "Only N rooms of
   <room> are left for these dates". These are the cart's rules; the messages name the room because a group has
   several.
5. The result is `PricedGroup(hotel card + timezone and check-in times, checkIn, checkOut, List<PricedLine(line,
   RoomRateView, RateOfferView, rules)>)`.

**Taxes.**
- `TaxRule` maps the rest of its columns: `calcType` (new enum `TaxCalcType`), `value`, `currency` and
  `appliesAfterDiscount`.
- `TaxRuleRepositoryCustom.findApplicable(countryCode, firstNight, lastNight)` (JPQL) returns the active rules that
  overlap the nights, ordered by id.
- `tax/model/TaxCalculator` is pure Java, like `CancellationSchedule`. It takes:
  - the currency
  - the nights, with each night's room price summed over every room
  - the rooms and guests counts
  - the subtotal and the discount
  - the rules

  It returns `List<TaxLine(ruleId, name, included, amountMinor)>`.
  - Each rule covers only the nights inside `[validFrom, validTo]`.
  - `PERCENT` uses `BigDecimal`: `onTop = round(rangePrice × factor × v / 100)` and
    `included = round(rangePrice × factor × v / (100 + v))`. `factor = (subtotal − discount) / subtotal` when
    `appliesAfterDiscount`, else 1. The factor is applied before rounding, never per night.
  - Fixed types use `Money.toMinor(value × count, currency)`.
  - A fixed rule whose currency isn't the booking's throws `BusinessException(CONFLICT, "Taxes for this hotel can't
    be calculated yet")`, the way `SearchCriteria.of` throws `BusinessException`.

**Discounts** (`discount/services/DiscountService` + impl; A-DISC-01 adds the admin side later):
- `AppliedDiscount check(String code, long userId, long hotelId, String currency, long subtotalMinor)` runs the
  checks in the plan's order and returns `{codeId, code, amountMinor}`. It throws `BusinessException(BAD_REQUEST,
  <message>)`.
- `AppliedDiscount reserve(...)` does the same, but loads the row with `PESSIMISTIC_WRITE` (`findByCodeForUpdate`),
  so phase 3's limit checks are serialized.
- `DiscountCode` holds the rules that need no query:
  - `isLive(now)`; whether it applies to a hotel (`appliesToAllHotels || hotelIds.contains`)
  - the currency rule; the minimum spend
  - `amount(subtotal)`, with the cap and never more than the subtotal
- `DiscountCodeRepositoryCustom` holds the JPQL: `findLiveByCode(code)`, which excludes deleted codes,
  `findByCodeForUpdate(code)`, `countUses(codeId)` and `countUses(codeId, userId)`.
- "First booking only" is checked with `BookingRepository.existsByUserIdAndStatusIn(userId, LIVE)` (a derived
  query; LIVE = PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, NO_SHOW).

**Booker pre-fill.** `UserRepository.findById(userId)` → the full name, email, phone and country.

**API.** `BookingController` at `/api/bookings` → `BookingService` → `BookingServiceImpl` (`@Transactional(readOnly
= true)` class, like the cart). `BookingMapper` (`@Component`, uses `HotelMapper.toRate` for each line's
cancellation, as `CartMapper` does) builds the `CheckoutQuote`.

**Rate limit.**
- `RateLimitPolicy.CHECKOUT(post("/api/bookings/quote", "/api/bookings"))`
- `RateLimitProperties.Limits.checkout`
- `application.yml` `app.rate-limit.limits.checkout: {capacity: 20, period: 1m}`, with the comment updated
- a `RateLimitPolicyTest` case

## Related Code Files

- Create:
  - `booking/controller/BookingController.java`
  - `booking/dto/{CheckoutRequest,CheckoutQuote,QuoteHotel,QuoteLine,PriceBreakdown,AppliedDiscountResponse,TaxAmount,BookerDetails}.java`
    (names may merge while writing)
  - `booking/mapper/BookingMapper.java`, `booking/services/BookingService.java`,
    `booking/services/impl/BookingServiceImpl.java`
  - `tax/model/{TaxCalcType,TaxCalculator,TaxLine}.java`
  - `discount/services/DiscountService.java`, `discount/services/impl/DiscountServiceImpl.java`,
    `discount/services/AppliedDiscount.java`
  - `discount/repository/DiscountCodeRepositoryCustom.java`,
    `discount/repository/impl/DiscountCodeRepositoryImpl.java`
  - tests: `tax/model/TaxCalculatorTest.java` (plain JUnit), `booking/controller/BookingControllerQuoteTest.java`,
    `booking/controller/BookingControllerDiscountTest.java`, `booking/controller/BookingClient.java` (like
    `CartClient`)
- Modify:
  - `tax/model/TaxRule.java`, `tax/repository/TaxRuleRepositoryCustom.java`,
    `tax/repository/impl/TaxRuleRepositoryImpl.java`
  - `booking/repository/BookingRepository.java`
  - `common/ratelimit/{RateLimitPolicy,RateLimitProperties}.java`, `application.yml`,
    `common/ratelimit/RateLimitPolicyTest.java`

## Implementation Steps

1. Write `TaxCalculator` and `TaxCalculatorTest` first. Cases:
   - VAT 10% on top, with and without a discount, and with `appliesAfterDiscount = false`
   - VAT 8% included
   - per night × 2 rooms × 3 nights
   - per person per night, counting children
   - per booking
   - a rule that ends mid-stay
   - rounding of half a minor unit in VND and in USD
   - a fixed rule in another currency → 409
2. Map the rest of `TaxRule` and add `findApplicable`. Run the search and hotel tests, which read `TaxRule`.
3. Write the `DiscountCode` rules, the repository JPQL and `DiscountServiceImpl.check`/`reserve`.
4. Write the pricing core, `quote`, the mapper and the controller.
5. Add the rate limit.
6. Write `BookingControllerQuoteTest`:
   - two room types and three rooms, with VAT 10% on top in an `XA`–`XZ` test country → the expected lines,
     subtotal, tax and total
   - the line prices equal `GET /api/cart`'s group for the same lines
   - an included tax is listed but not added
   - lines of two hotels → 400; lines of two date ranges → 400
   - another user's item → 404
   - a closed night → 409 naming the room; a past check-in (fixture line) → 409
   - more rooms than left across two lines of one room type → 409
   - the booker is pre-filled from the saved profile
   - without a token → 401
7. Write `BookingControllerDiscountTest`, with each rule's message:
   - unknown; inactive; deleted
   - not started; ended
   - another hotel; another currency
   - minimum spend
   - total limit; per-user limit (seed a redemption with JDBC)
   - first booking only (seed a booking with `CatalogFixtures.booking`)
   - a percent code capped at `max_discount_minor`; a fixed code
   - case-insensitive match
   - tax charged on the discounted price

## Success Criteria

- [x] The quote matches the cart's prices for the same lines, and its totals add up (`total = subtotal − discount +
  taxes on top`).
- [x] Every tax type and discount rule is covered by a test with its message.
- [x] The search, hotel page and cart tests are unchanged and green.

## Risk Assessment

- **A test country's tax rules apply to all its hotels**, so each test uses its own `XA`–`XZ` code (fixture rule,
  as in the tax tests today).
- **Codes share one table across tests.** Codes get a `uniqueSuffix()`, and tests assert only on their own codes.
- **Proportional tax after a discount** could drift by a minor unit if rounded per night. Rounding once per rule,
  which the unit tests pin, avoids that.
