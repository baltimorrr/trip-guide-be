---
phase: 3
title: Room list with rates and cancellation
status: completed
priority: P1
dependencies:
  - 1
  - 2
---

# Phase 3: Room list with rates and cancellation

## Overview

`GET /api/hotels/{slug}/rooms?checkIn&checkOut&rooms&adults&childAges` is public. It covers C-HOTEL-04: every
sellable room type, each with every sellable rate plan, room details, `roomsLeft`, `fitsGuests`, and price per night
and per stay. It also covers C-HOTEL-06: each rate gets `freeUntil` plus a priced penalty timeline. "Free rooms",
"fits the guests" and "can sell the stay" keep one SQL definition each, shared with search. Search's SQL and
results must not change.

## Context

- `spring-base/CLAUDE.md` "Hotel catalog and search":
  - native SQL only for the priced availability chain
  - `night_price()` is the only price definition
  - "fits": children may use free adult places
  - a room is available only when an active plan in the hotel's currency can sell every night
  - `FreeCancellation.until` is the twin of `free_cancellation_until()`; don't change it
  - Hibernate rejects binding a parameter the SQL text doesn't use
- `hotel/repository/impl/HotelRepositoryImpl.java`:
  - `PRICED_OFFERS` (`nights`, `free_rooms`, `rate_offers`, `offers`)
  - `bind(...)`
  - how `availableOffers` reads `Object[]` rows
- `search/services/impl/SearchServiceImpl.java`: `prepare` (`SearchCriteria.of`, `LATEST_TIME_ZONE`), `results` (the
  ids-then-JPQL pattern, taxes)
- `search/mapper/SearchMapper.toOffer`: the `FreeCancellation.until` call
- `rate/repository/impl/RatePlanRepositoryImpl.OFFER_DETAILS`, `RateOfferView`
- `search/model/FreeCancellation.java`, `FreeCancellationTest`, `FreeCancellationSqlTest`
- Rule semantics: a rule means "cancelling less than N hours before check-in costs the penalty", and the rule that
  applies is the one with the smallest N still greater than the hours left (V5 comment, `CancellationPolicyRule` doc)
- Tests: `SearchControllerPricingTest` (stays, rate rules, overrides, closed nights, minimum stays, taxes with
  `XA`–`XZ`)

## Requirements

- Functional: the room-list and cancellation rules in `plan.md` "Planner Defaults" and "User Decisions". The stay
  rules and their 400s are the same as search's (`SearchCriteria.of` with `SearchProperties`). An unknown or
  unlisted hotel gives 404.
- Consistency:
  - For one room, the cheapest fitting rate here is the rate plan and total that the search card shows for the same
    stay.
  - `freeUntil` here equals the card's.
  - The first listed penalty starts at `freeUntil` when one is set.
- Non-functional:
  - One native query, plus at most 8 small JPQL/derived queries. None of them run when nothing is sellable.
  - The native query prices only this hotel.

## Architecture

### Shared SQL fragments (refactor inside `HotelRepositoryImpl`, no behaviour change)

Split `PRICED_OFFERS` so the room list can reuse its first three CTEs. Use string concatenation, the way
`HOTEL_ROWS` + location + `HOTEL_ROWS_END` already works:

```text
ROOM_TYPE_FITS   rt.max_adults >= :adultsPerRoom
                 and rt.max_occupancy >= :adultsPerRoom + :childrenPerRoom
                 and :childrenPerRoom <= rt.max_children + (rt.max_adults - :adultsPerRoom)
NIGHTS           nights as (...)                                         unchanged text
FREE_ROOMS       free_rooms as (... where rt.status = 'ACTIVE' and rt.deleted_at is null<extra> group by ...)
RATE_OFFERS      rate_offers as (...)                                    unchanged text
OFFERS           offers as (...)                                         search only

search:     NIGHTS, FREE_ROOMS(extra = "\n  and " + ROOM_TYPE_FITS), RATE_OFFERS, OFFERS   → same SQL as today
room list:  NIGHTS, FREE_ROOMS(extra = ""),                          RATE_OFFERS            → every active room type
```

Each fragment ends at its CTE's closing parenthesis, and the callers add `,\n`. Document `ROOM_TYPE_FITS` as the only
definition of "a room type holds the per-room guests". Optional but cheap: print the search's rendered SQL before and
after the split, then diff it.

### Room list native query: `HotelRepositoryCustom.findRoomRates(long hotelId, StayRequirement stay)`

```sql
with candidate_hotels as (select h.id from hotels h where h.id = :hotelId and <HotelSql.HOTEL_LISTED>),
<NIGHTS>,
<FREE_ROOMS without the fit condition>,
<RATE_OFFERS>
select r.room_type_id, r.rate_plan_id, r.free, (<ROOM_TYPE_FITS>) as fits, r.stay_price,
       array(select night_price(r.rate_plan_id, n.stay_date) from nights n order by n.stay_date) as night_prices
from rate_offers r
join room_types rt on rt.id = r.room_type_id
order by fits desc, r.stay_price, rt.sort_order, rt.id, r.rate_plan_id
```

- The parameters bound are exactly the ones the text uses: `hotelId, checkIn, checkOut, nights, adultsPerRoom,
  childrenPerRoom`. Never `:rooms`, `:now` or `:priceCurrency`, so add a small bind for this query instead of reusing
  `bind(...)`.
- `rate_offers` already drops plans that can't sell the stay and room types with `free = 0`, so sold-out and
  unsellable room types and plans never appear.
- `night_prices` exists only for `NIGHTS` penalties. It calls `night_price()` again for this one hotel's plans
  (≤ plans × 30 nights), and search is left untouched.
- Returns `List<RoomRateView(roomTypeId, ratePlanId, roomsLeft, fits, stayPriceMinor, List<Long> nightPricesMinor)>`,
  in query order. The class doc's native-SQL justification gains one sentence for this query.

### JPQL for the room types and rates on the page

- New `hotel/repository/RoomTypeRepository` is a plain interface plus a `@Repository` `impl/RoomTypeRepositoryImpl`
  (the `WishlistRepository` pattern: nothing for Spring Data to generate):
  - `findDetails(ids)` → `RoomTypeDetailView(id, name, description, sizeSqm, maxAdults, maxChildren, maxOccupancy)`
  - `findBeds(ids)` → `RoomBedView(roomTypeId, code, name, quantity)`, via `from RoomType rt join rt.beds b join
    b.bedType bt`, ordered by bed type id
  - `findFeatures(ids)` → `RoomFeatureView(roomTypeId, code, name, icon)`, via `from RoomType rt join rt.features am`
    with `am.scope = ROOM`, ordered by group, then name
  - `findPhotos(ids)` → `RoomPhotoView(roomTypeId, url)`, from `HotelPhoto p where p.roomTypeId in :ids`, ordered by
    `sortOrder`, then id
- `RatePlanRepositoryCustom.findCancellationRules(ratePlanIds)` → `CancellationRuleView(ratePlanId,
  hoursBeforeCheckIn, penaltyType, penaltyValue)`, via `from RatePlan rp join CancellationPolicyRule r on r.policy =
  rp.cancellationPolicy`
- Reused as they are: `RatePlanRepository.findOfferDetails` (names, board, policy name, `refundable`,
  `penaltyFromHours`, zone, check-in time), `TaxRuleRepository.findCountriesWithTaxesOnTop`,
  `HotelRepository.findBySlugAndStatusAndDeletedAtIsNull`

### Cancellation timeline: `search/model/CancellationSchedule` (pure, next to `FreeCancellation`)

`static List<Tier> tiers(LocalDate checkIn, LocalTime checkInFrom, ZoneId zone, List<Rule> rules, List<Long>
nightPricesMinor, String currency, Instant now)`, with `record Rule(int hoursBeforeCheckIn, PenaltyType type,
BigDecimal value)` and `record Tier(OffsetDateTime from, OffsetDateTime until, long penaltyMinor)`.

1. `C` = check-in day at `checkInFrom` in `zone`. Sort the rules by hours ascending: h₁ < … < hₖ.
2. Tier i applies after `C − hᵢ` and up to `C − hᵢ₋₁`. Tier 1 has no end: it also covers cancelling after check-in
   and a no-show. Use `minusHours` on the instant timeline, as `FreeCancellation` does.
3. The amount for one room, with `stay = Σ nightPrices`, is capped at `stay`:
   - `PERCENT`: `stay × v / 100`, half up
   - `NIGHTS`: the first ⌊v⌋ nights, plus the fraction × the next night (half up)
   - `FIXED`: `Money.toMinor(v, currency)`
4. Tiers are computed from all rules, so a zero rule still bounds its neighbours. Then drop the tiers whose rule
   value is 0 (the same predicate `penaltyFromHours` uses) and the tiers with `until ≤ now`. Set `from = null` when it
   is `≤ now`.
5. Return them in time order (i = k … 1). The first tier's `from` then equals `FreeCancellation.until(...)` whenever
   that is non-null. A unit test asserts this over several policies.

`freeUntil` keeps coming from `FreeCancellation.until`. Don't re-implement it. `FreeCancellation` and its SQL twin
stay unchanged.

### Service flow: `HotelService.rooms(slug, StayRequest)`

```text
hotel = hotels.findBySlugAndStatusAndDeletedAtIsNull(slug, ACTIVE) or 404
criteria = SearchCriteria.of(..., SearchCriteria.today(), limits)       400s as in search
rows = hotels.findRoomRates(hotel.id, stay)                             empty → RoomList with no room types
details/beds/features/photos = roomTypes.find*(roomTypeIds)
rates = ratePlans.findOfferDetails(ratePlanIds); rules = ratePlans.findCancellationRules(ratePlanIds)
taxesIncluded = !taxRules.findCountriesWithTaxesOnTop({country}, checkIn, checkOut − 1).contains(country)
now = Instant.now() (one value for every rate)
mapper.toRoomList(...): group rows by room type in first-seen order; rates keep row order
```

Move `LATEST_TIME_ZONE` and its comment from `SearchServiceImpl` into `SearchCriteria` as `static LocalDate today()`,
and call it from both services: one definition of "today" for stay checks. `SearchCriteria.of` still takes `today`, so
its tests don't change.

### Response (`RoomList`)

```json
{
  "checkIn": "2026-11-01", "checkOut": "2026-11-03", "nights": 2, "rooms": 1, "adults": 2, "children": 0,
  "currency": "VND", "taxesIncluded": false,
  "roomTypes": [{
    "roomTypeId": 12, "name": "Deluxe Double", "description": "…", "sizeSqm": 28.0,
    "maxAdults": 2, "maxChildren": 0, "maxOccupancy": 2,
    "beds": [{"code": "king", "name": "King bed", "quantity": 1}],
    "features": [{"code": "air_conditioning", "name": "Air conditioning", "icon": null}],
    "photoUrls": ["…"], "roomsLeft": 5, "fitsGuests": true,
    "rates": [{
      "ratePlanId": 40, "name": "Room only, free cancellation", "boardType": "RO", "boardTypeName": "Room only",
      "pricePerNight": {"amount": 1500000, "currency": "VND"}, "totalPrice": {"amount": 3000000, "currency": "VND"},
      "cancellation": {
        "refundable": true, "policyName": "Free until 72 hours before check-in",
        "freeUntil": "2026-10-29T14:00:00+07:00",
        "penalties": [
          {"from": "2026-10-29T14:00:00+07:00", "until": "2026-10-31T14:00:00+07:00",
           "amount": {"amount": 1500000, "currency": "VND"}},
          {"from": "2026-10-31T14:00:00+07:00", "until": null, "amount": {"amount": 3000000, "currency": "VND"}}]}
    }]
  }]
}
```

Prices and penalties are for one room. The client multiplies by the quantity it lets the guest pick, up to
`roomsLeft` across all rates of that room type.

## Related Code Files

Paths under `spring-base/src/main/java/com/company/project/` unless shown in full.

- Create:
  - `hotel/repository/RoomRateView.java`, `RoomTypeDetailView.java`, `RoomBedView.java`, `RoomFeatureView.java`,
    `RoomPhotoView.java`, `RoomTypeRepository.java`, `impl/RoomTypeRepositoryImpl.java`
  - `rate/repository/CancellationRuleView.java`
  - `search/model/CancellationSchedule.java`
  - `hotel/dto/StayRequest.java`, `RoomList.java`, `RoomOption.java`, `RoomBed.java`, `RoomFeature.java`,
    `RateOption.java`, `RateCancellation.java`, `CancellationPenalty.java`
  - tests: `src/test/java/com/company/project/hotel/controller/HotelControllerRoomsTest.java`,
    `src/test/java/com/company/project/search/model/CancellationScheduleTest.java`,
    `src/test/java/com/company/project/hotel/services/impl/HotelServiceImplTest.java`
- Modify:
  - `hotel/repository/HotelRepositoryCustom.java`, `hotel/repository/impl/HotelRepositoryImpl.java` (the fragment
    split + `findRoomRates`)
  - `rate/repository/RatePlanRepositoryCustom.java`, `rate/repository/impl/RatePlanRepositoryImpl.java`
  - `search/model/SearchCriteria.java` (`today()`), `search/services/impl/SearchServiceImpl.java` (use it)
  - `hotel/services/HotelService.java`, `hotel/services/impl/HotelServiceImpl.java`, `hotel/mapper/HotelMapper.java`,
    `hotel/controller/HotelController.java`
  - `src/test/java/com/company/project/support/CatalogFixtures.java` (only if a test needs a helper the phase 1 set
    lacks)
- Delete: none

## Implementation Steps

1. Split `PRICED_OFFERS` into the fragments. Run `./mvnw test -Dtest='SearchController*Test,FreeCancellationSqlTest'`.
   Everything must pass unchanged before going on.
2. `findRoomRates` + `RoomRateView`. Read the `night_prices` column defensively (see Risks).
3. `RoomTypeRepository` + impl + views; `findCancellationRules` + view.
4. `CancellationSchedule` + `CancellationScheduleTest`, written first. This is the domain rule, so pin it before the
   wiring.
5. `SearchCriteria.today()`; switch `SearchServiceImpl` to it.
6. DTOs, `HotelMapper.toRoomList`, `HotelService.rooms`, `HotelController` `GET /{slug}/rooms` (`@Valid
   @ParameterObject StayRequest`, `@SecurityRequirements`). `StayRequest` copies the stay fields' Bean Validation
   from `HotelSearchRequest`.
7. `HotelControllerRoomsTest` and `HotelServiceImplTest`, then run every search, wishlist and hotel test class.

## Tests

`CancellationScheduleTest` (unit):
- the two-tier policy (72 h 50 %, 24 h 100 %): exact `from`/`until` in the hotel zone, amounts, time order
- the first tier's `from` equals `FreeCancellation.until` for: one-rule, two-tier, zero-value-first and
  all-zero policies
- the non-refundable rule (100000 h) gives one tier with `from = null`, `until = null` and the whole stay
- a tier already over is dropped, and a tier in effect has `from = null`
- `NIGHTS` 1, 1.5 and more than the stay (capped); `FIXED` in VND and in USD (decimals), capped at the stay
- a zero-value rule bounds its neighbours but isn't listed
- a daylight-saving zone (e.g. `Europe/Berlin` across the October change) counts hours on the instant timeline

`HotelControllerRoomsTest` (`@IntegrationTest`):
- two plans on one room type (RO free, BB non-refundable) are both listed, cheapest first. Not listed: an inactive
  plan, a plan in another currency, a plan with a closed night, a plan whose minimum stay is longer than the stay.
- `roomsLeft` is the fewest free rooms across nights (inventory rows) and is the same on every rate. Not listed: a
  room type sold out or closed on one night, and an `INACTIVE` or soft-deleted room type.
- `fitsGuests`:
  - a room type too small for the per-room adults is listed after the fitting ones, with `fitsGuests: false`
  - children using free adult places still fit, as in search
  - `rooms=2&adults=3` uses 2 adults per room
- details: description, size, beds with quantity, ROOM-scope features only, and room photos in order
- prices: the total follows `night_price()` (a rate rule and a date override). `pricePerNight` is rounded half up
  (3 nights, total not divisible).
- `taxesIncluded` is false for a hotel in a country (`XA`–`XZ`) with a tax rule on top.
- **matches search**: for the same stay with `rooms=1`, `/api/search/hotels?hotel={slug}` gives
  `offer.roomLines[0]` with the same `ratePlanId`, total and `cancellation.freeUntil` as the room list's first
  fitting rate.
- cancellation: the two-tier policy's `freeUntil` and `penalties` (from/until/amount); non-refundable; a `NIGHTS` rule
  and a `FIXED` rule.
- 400 for a past check-in, check-out not after check-in, too many rooms, rooms > adults, or a missing parameter. 404
  for an unknown or `INACTIVE` hotel. A listed hotel with nothing sellable gives 200 with empty `roomTypes`. No token
  gives 200.

`HotelServiceImplTest` (Mockito): no sellable row → no room-type, rate, rule or tax query, and an empty list.

## Success Criteria

- [x] The endpoint returns the documented shape with the rules from `plan.md`.
- [x] The "matches search" test passes. Every pre-existing search test passes unchanged after the fragment split.
- [x] `CancellationScheduleTest` pins the tier rule, including its agreement with `FreeCancellation.until`.
- [x] Nothing past the native query runs when it returns no rows, except the tax query: `taxesIncluded` doesn't
      depend on the rooms, so it is computed for an empty list too (see plan.md Implementation Notes).

## Risk Assessment

- **Refactoring search's native SQL text.** Mitigation: the split is concatenation only, and step 1 runs the full
  search suite before anything else changes (optionally with a diff of the rendered SQL). If any search test fails,
  revert the split and copy the three CTEs into the room-list query with a "keep in sync" comment. That is the
  fallback, not the plan.
- **How Hibernate 7 returns `bigint[]` from a native query.** It may be a `Long[]` or a `java.sql.Array`, depending on
  type inference. Handle both when reading (`row[5] instanceof java.sql.Array a ? a.getArray() : row[5]`), or use
  `query.unwrap(NativeQuery.class).addScalar("night_prices", …)`. The tests show which.
- **Penalty semantics guessed for later stories** (`FIXED` per room, fractional `NIGHTS` pro rata). They live in one
  pure class with tests, so C-MYBK-03 can change them in one place. Listed in Open Questions.
- **`roomsLeft` exposes exact inventory.** That was accepted as a default. The client decides when to show "Only N
  left".
- **Even split for `fitsGuests`.** Guests composing an uneven mix see `fitsGuests: false` on rooms that would work in
  their mix. The flag is advisory, and cart validation (C-CART-01) checks guests per room.
