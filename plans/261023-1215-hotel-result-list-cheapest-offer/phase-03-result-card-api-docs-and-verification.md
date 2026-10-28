---
phase: 3
title: 'Result card API, docs and verification'
status: completed
priority: P2
dependencies:
  - 2
---

# Phase 3: Result card API, docs and verification

## Overview

This phase builds the C-SRCH-01 card. `SearchServiceImpl` combines the priced page, cards, photos, offer details and
tax flag, and computes the free-cancellation deadline. Two new small types do the money and deadline logic. Tests
cover the story's acceptance criteria, and README, CLAUDE.md and USER_STORIES.md are updated.

## Context

- `search/services/impl/SearchServiceImpl.java` (`searchHotels`, `cardsInOrder`), `search/mapper/SearchMapper.java`,
  `search/dto/HotelSearchResult.java`, `search/controller/SearchController.java` (javadoc mentions "ordered by user
  rating").
- `search/model/SearchCriteria.java` is the style to copy for small rule records with unit tests
  (`SearchCriteriaTest`).
- Tests: `SearchControllerTest` (style, helpers `search`, `names`, `stay()`), `support/CatalogFixtures` (phase 1
  additions), and the Tests section of `spring-base/CLAUDE.md`.

## Requirements

- Functional: response shape as in `plan.md` "API Change". `photoUrls` holds at most `MAX_CARD_PHOTOS = 10` hotel
  photos, cover first. `offer.totalPrice` = Σ `quantity × stayPrice` over the lines. `nights` comes from the
  criteria. `cancellation.freeUntil` follows the rule below.
- Non-functional: 4 small queries per page after the native one (cards, photos, offer details, tax countries), each
  `in (:ids)`. No N+1. Keep `@Transactional(readOnly = true)`.

## Architecture

New types:

| Type | Package | Rule |
|------|---------|------|
| `Money(BigDecimal amount, String currency)` | `common/dto` | `static Money ofMinor(long minor, String currency)`: scale = `Currency.getInstance(code).getDefaultFractionDigits()`, at least 0 (VND 0 → `2400000`, USD 2 → `129.50`) |
| `FreeCancellation` | `search/model` | `static OffsetDateTime until(LocalDate checkIn, LocalTime checkInFrom, ZoneId zone, boolean refundable, Integer penaltyFromHours, Instant now)`: null if not refundable; `at = ZonedDateTime.of(checkIn, checkInFrom, zone)`; `deadline = penaltyFromHours == null ? at : at.minusHours(penaltyFromHours)`; null if `!deadline.toInstant().isAfter(now)`; else `deadline.toOffsetDateTime()` |
| `StayOffer(int nights, int rooms, Money totalPrice, boolean taxesIncluded, List<OfferRoom> roomLines)` | `search/dto` | |
| `OfferRoom(Long roomTypeId, String roomTypeName, Long ratePlanId, String ratePlanName, int quantity, String boardType, String boardTypeName, CancellationTerms cancellation, Money price)` | `search/dto` | `price` = quantity × stay price |
| `CancellationTerms(boolean refundable, OffsetDateTime freeUntil, String policyName)` | `search/dto` | Client: `freeUntil` → "Free cancellation until 12 Oct"; refundable without `freeUntil` → policy name; else "Non-refundable" |

Why the deadline rule is right: a rule means "cancelling less than N hours before check-in costs the penalty",
and the smallest N that is still greater than the hours left applies. So cancelling stays free until the largest
N with a positive penalty starts to apply. `penaltyFromHours` is that N (`RateOfferView`, phase 2).

`HotelSearchResult`: replace `String coverPhotoUrl` with `List<String> photoUrls`, and append `StayOffer offer`.

`SearchServiceImpl.searchHotels` flow (validation unchanged):

```
Page<HotelOfferView> offers = hotels.findAvailable...(…, stay, pageable)
ids          = offers.content → hotelId (in order)
cards        = hotels.findCardsByIdIn(ids)                 → Map by id
photos       = hotels.findHotelPhotos(ids)                 → Map<hotelId, List<url>> capped at MAX_CARD_PHOTOS
details      = ratePlans.findOfferDetails(all ratePlanIds) → Map by ratePlanId
taxCountries = taxRules.findCountriesWithTaxesOnTop(countryCodes of cards, checkIn, checkOut - 1 day)
now          = Instant.now()
content      = offers.map(o -> searchMapper.toResult(card, photos, o, details, criteria, taxesIncluded, now))
```

The mapper builds `OfferRoom`s (deadline through `FreeCancellation.until(...)`, using `ZoneId.of(detail.hotelTimezone())`)
and totals with `Money.ofMinor`. The controller javadoc changes to "cheapest room mix per hotel, ordered by user
rating; a `sort` parameter is ignored".

## Related Code Files

- Create: `common/dto/Money.java`, `search/model/FreeCancellation.java`,
  `search/dto/{StayOffer,OfferRoom,CancellationTerms}.java`
- Modify: `search/dto/HotelSearchResult.java`, `search/mapper/SearchMapper.java`,
  `search/services/impl/SearchServiceImpl.java`, `search/services/SearchService.java` (javadoc),
  `search/controller/SearchController.java` (javadoc)
- Tests, create: `src/test/.../common/dto/MoneyTest.java`, `src/test/.../search/model/FreeCancellationTest.java`,
  `src/test/.../search/controller/SearchControllerPricingTest.java`
- Tests, modify: `src/test/.../search/controller/SearchControllerTest.java` (`coverPhotoUrl` →
  `photoUrls[0]`, plus `photoUrls` order: cover first)
- Docs: `spring-base/README.md`, `spring-base/CLAUDE.md`, `spring-base/USER_STORIES.md`

## Implementation Steps

1. `Money` + `MoneyTest` (VND, USD, zero).
2. `FreeCancellation` + `FreeCancellationTest`: not refundable → null; no penalty rule → check-in time; 24h → the
   day before at check-in time with `+07:00`; deadline passed → null; a DST zone (Europe/Paris, check-in on the day
   after the switch, 24h) → hours on the instant timeline.
3. DTOs, mapper, service as above. Delete `cardsInOrder` or fold it into the new flow.
4. `SearchControllerPricingTest` (`@IntegrationTest`, own city per test via `uniqueSuffix()`, call
   `syncService.sync()`):
   - `showsTheCheapestRatePlanOfTheCheapestRoomType`: 2 room types × 2 plans. The card has the cheapest line's
     names, board, nights = 2, total = 2 nights × price, and `offer.rooms` = 1.
   - `nightPriceUsesOverrideThenHighestPriorityRuleThenBase`: base 100, a weekday rule (+50%, priority 1), a
     lower-priority SET_PRICE rule, and an override on one night. Choose stay dates whose ISO weekdays are known
     (compute from `CHECK_IN`).
   - `closedRateNightOrUnmetMinimumStaySkipsThatRatePlan`: the cheaper plan closed on one night (or `minNights`
     3 for a 2-night stay) → the dearer plan shows. If every plan is excluded, the hotel is hidden.
   - `roomTypeWithoutSellableRatePlanDoesNotCount`: `bareRoomType`, or an inactive plan, or a plan in another
     currency → hidden.
   - `severalRoomsFillFromTheCheapestRoomType`: cheap type with 1 free + dearer type with 3 free, `rooms=2` →
     lines [cheap×1, dear×1], total = sum. `rooms=1` → one line.
   - `taxesIncludedFollowsTheCountrysRules`: hotel in `XA` with an on-top rule valid during the stay → false. Hotel
     in `XB` with only an included rule, or an on-top rule that expired before check-in → true. Search by hotel
     slug.
   - `freeCancellationDeadlineIsInTheHotelsTimeZone`: refundable policy, rule 48h 100% → `freeUntil` = check-in − 2
     days at 14:00 `+07:00`. A non-refundable plan → `refundable=false`, `freeUntil` null.
   - `cardListsHotelPhotosCoverFirstWithoutRoomPhotos`: room photo excluded, cover first.
   - `pagesKeepTheirOrderAndTotal`: 3 hotels, `size=2` → page 0 has 2, page 1 has 1, `totalElements` 3, and the lines
     belong to their hotels.
5. Docs:
   - README: endpoint row ("paged; available hotels with their cheapest offer"). "Search and destinations": a
     **Prices** paragraph (night price order, rate plan sellability, cheapest room mix, money format, `taxesIncluded`
     meaning, free-cancellation rule, photos). Package tree (`rate/`, `tax/`). Test table rows (`SearchControllerPricingTest`,
     `FreeCancellationTest`, `MoneyTest`). The "copy only what you need" list (V5, `rate/`, `tax/`). Known limitations:
     replace "checks availability only" with sort ignored (C-SRCH-03), prices in the hotel's currency (C-SRCH-05),
     taxes are a flag (amounts at checkout), price doesn't depend on guest count or child ages, no write APIs for
     rates/policies/taxes, the even guest split, and country-wide search cost of `night_price()`.
   - CLAUDE.md: "Implemented so far" adds C-SRCH-01 (`rate/`, `tax/`, migration V5). The native-query list adds the
     priced availability (`night_price()`, `distinct on`, running sum). Note that `night_price()` is the only night
     price definition, like `norm()`. Tests: `CatalogFixtures.roomType` adds a default rate plan (`bareRoomType` has
     none), and tax tests use their own `XA`–`XZ` country codes.
   - USER_STORIES.md: mark C-SRCH-01 "✅ Implemented". Note that the detail page itself is C-HOTEL-01/04. In §10 tick
     "Are sold-out hotels hidden from search or shown as 'Sold out'?" → hidden. Leave the season-pricing question to
     the user (it is flagged in `plan.md`).
6. Verification: `./mvnw verify`. Then run locally (this machine uses port 8081 and DB port 5434 per `.env`):
   `curl "$BASE/api/search/hotels?destination=vn-da-nang&checkIn=<+1d>&checkOut=<+3d>&rooms=1&adults=2"` shows prices.
   `rooms=2&adults=4` on a family search shows a mix where only one Family Suite is free.

## Success Criteria

- [ ] Every acceptance criterion in `plan.md` is covered by a named test above.
- [ ] `./mvnw verify` is green, with no skipped or weakened existing tests.
- [ ] Local curl shows `offer.totalPrice`, `roomLines[].boardType`, `cancellation.freeUntil` (+07:00) and
      `taxesIncluded: false` (seeded VN VAT on top).
- [ ] Docs match the behaviour: dates, endpoint row, limitations.

## Risk Assessment

- **Invalid `hotels.timezone`** makes `ZoneId.of` throw a 500. The seed and fixtures use valid zones, and the owner
  hotel API (O-HOTEL-01) must validate them. Note it in README Known limitations rather than catch it.
- **Shared test DB**: tax rules apply to a whole country, so never add a `VN` rule in tests. Popular/featured
  assertions stay relative.
- **Contract change** (`coverPhotoUrl` → `photoUrls`): no client exists yet. `PopularHotel` keeps `coverPhotoUrl`.
- **Rollback**: revert the files in `spring-base` (the user's git). V5 stays applied locally unless the user runs
  `docker compose down -v`.
