---
title: Hotel result list with cheapest offer (C-SRCH-01)
description: >-
  Turn the availability-only hotel search into the result list of C-SRCH-01:
  rate plans, board types, cancellation policies, season/weekend rules and date
  overrides priced by one SQL night_price(); each card shows its cheapest offer
  for the whole stay (cheapest room mix for multi-room searches), a
  free-cancellation deadline in the hotel's time zone, a photo list and whether
  taxes are included.
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - search
blockedBy: []
blocks: []
created: 2026-10-23T00:00:00.000Z
---

# Hotel result list with cheapest offer (C-SRCH-01)

## Overview

Sources: `spring-base/USER_STORIES.md` §3 (C-SRCH-01), §8 (O-ROOM-02/03, A-CONF-02), §10 (NFRs, open questions);
`spring-base/DATABASE-SCHEMA.md` group `rooms_rates_availability` and `tax_rules`.
Builds on the completed [homepage search plan](../261021-1223-home-search-and-popular-destinations/plan.md):
`GET /api/search/hotels` already returns paged available hotels (count in `page.totalElements`), with stars, ratings,
distance and a cover photo. Missing: prices, rate plans, board types, cancellation terms, taxes, photo list.

All other plans in `plans/` are completed. No cross-plan dependency.

## User Decisions (2026-10-23, do not reverse silently)

| Decision | Choice |
|----------|--------|
| Scope | **Hold**: story as written. No owner/admin rate or tax APIs (O-ROOM-02/03, A-CONF-02) |
| Sold-out hotels | **Hidden** (current behaviour). Resolves the story's "decision needed" |
| Multi-room price | **Cheapest mix**: fill rooms from the cheapest room type up; the offer lists 1+ room lines. Keeps C-HOME-01's tested rule that rooms may come from different types |
| Taxes on the card | **Flag only**: `taxesIncluded` from `tax_rules`. Tax amounts come with the checkout breakdown (C-BOOK-02) |
| Git | No commits or staging by Claude (standing preference) |

## Planner Defaults (changeable)

- A room counts as available only when an active rate plan can sell it on every night of the stay (not closed,
  minimum stay met). Room types without a sellable rate plan disappear from search.
- Night price order is the schema doc's: `rate_plan_prices` override, then the active `rate_rules` row with the
  highest priority (ties: newest id) covering the date and weekday, then `base_price_minor`. One SQL function
  `night_price()` is the only definition (search now; cart and booking later). Season rules are in scope: the
  22 Oct "Changes" table and O-ROOM-03 include them, so the still-open checkbox in §10 looks stale.
- Minimum stay = the largest `coalesce(rate_plan_prices.min_nights, rate_plans.min_nights)` over the stay's nights.
- Prices compare in the hotel's selling currency. Rate plans in another currency are ignored by search.
  Display-currency conversion is C-SRCH-05.
- `taxesIncluded` = no active tax rule of the hotel's country, valid on any night of the stay, has
  `is_included = false`. A country with no rules counts as included (checkout adds nothing).
- Free cancellation until = check-in day at `hotels.check_in_from` in `hotels.timezone`, minus the largest
  `hours_before_check_in` whose penalty is > 0. Null when the policy isn't refundable or that moment has passed.
- Money in JSON: `{"amount": 2400000, "currency": "VND"}`. The amount is in major units, scaled by the ISO 4217 digits
  from `java.util.Currency`. The database keeps `*_minor`.
- `HotelSearchResult.coverPhotoUrl` becomes `photoUrls` (hotel photos only, cover first, at most 10). There's no client
  yet. Popular destinations keep `coverPhotoUrl`.
- No `currencies` table yet. Currency columns are `varchar(3)` + a regex CHECK, and their FKs come with A-CONF-01.
- Rates and taxes are entered through the local seed and test fixtures only, as hotels are today.

## Phases

| Phase | Name | Status | Depends on |
|-------|------|--------|------------|
| 1 | [Rate and tax schema with night price](./phase-01-rate-and-tax-schema-with-night-price.md) | Completed | — |
| 2 | [Priced availability and offer details](./phase-02-priced-availability-and-offer-details.md) | Completed | 1 |
| 3 | [Result card API, docs and verification](./phase-03-result-card-api-docs-and-verification.md) | Completed | 2 |

Strictly sequential: each phase compiles against the previous one, and phase 2 changes repository signatures that
phase 3 consumes.

## API Change

`GET /api/search/hotels` (same parameters, same paging). Each `content[]` item:

```json
{
  "id": 7, "slug": "my-khe-beach-resort-demo", "name": "Mỹ Khê Beach Resort", "accommodationType": "resort",
  "starRating": 5, "photoUrls": ["https://.../cover.jpg", "..."], "district": "Sơn Trà", "city": "Đà Nẵng",
  "countryCode": "VN", "googleRating": 4.7, "googleRatingCount": null, "userRatingAvg": 9.2,
  "userRatingCount": 873, "distanceToCenterM": null,
  "offer": {
    "nights": 2, "rooms": 1, "totalPrice": {"amount": 2700000, "currency": "VND"}, "taxesIncluded": false,
    "roomLines": [{
      "roomTypeId": 21, "roomTypeName": "Deluxe Double", "ratePlanId": 40, "ratePlanName": "Breakfast, non-refundable",
      "quantity": 1, "boardType": "BB", "boardTypeName": "Breakfast included",
      "cancellation": {"refundable": false, "freeUntil": null, "policyName": "Non-refundable"},
      "price": {"amount": 2700000, "currency": "VND"}
    }]
  }
}
```

## Acceptance Criteria

- [x] Each card shows photos, name, stars, Google rating, user rating + count, distance, board type, cancellation
      terms ("free until …" in the hotel's offset), nights and the total price for the stay.
- [x] The price is the cheapest sellable combination for the requested rooms, guests and dates. Night prices follow
      override, then rule, then base. Closed nights and unmet minimum stays exclude a rate plan.
- [x] Multi-room: rooms are taken from the cheapest room type first. A hotel that only fits by mixing types is
      listed, with one line per room type.
- [x] `taxesIncluded` reflects the country's tax rules for the stay dates.
- [x] Hotels with no sellable room for the stay are hidden. `page.totalElements` is the result count. Paging works.
- [x] The card has the hotel `slug`, and every criterion is still a GET parameter (the detail page link reuses them).
- [x] Existing C-HOME-01/03 tests pass, with only the `coverPhotoUrl` → `photoUrls` assertion changed.
- [x] `./mvnw verify` passes. A local run with the seed returns priced Đà Nẵng results.

## Out of Scope

Sorting and filters (C-SRCH-02/03), currency conversion (C-SRCH-05), map (C-SRCH-06), the no-results state
(C-SRCH-07), the hotel detail and room list APIs (C-HOTEL-*), tax amounts (C-BOOK-02), promotions and discounts,
child-age pricing, owner and admin write APIs for rates, policies and taxes, and a `currencies` table.

## Open Questions

None blocking. Worth a later product check: whether a country with no tax rules should read "includes taxes",
and the stay-through reading of per-date minimum nights. The §10 open question on season pricing looks stale
(see defaults).

## Implementation Notes (2026-10-23)

- Done as planned. `./mvnw verify` passed: 180 tests, including the new `SearchControllerPricingTest` (9),
  `FreeCancellationTest` (5) and `MoneyTest`.
- Found while testing: with `hibernate.jdbc.time_zone: UTC`, Hibernate's default `TIME` mapping read
  `check_in_from` 14:00 as 21:00 on this +07:00 machine. `Hotel.checkInFrom` now uses
  `@JdbcType(LocalTimeJdbcType.class)` (noted in `spring-base/CLAUDE.md`).
- Local run (seed, port 8081): Đà Nẵng gives 4 priced hotels. Mỹ Khê is 2,700,000 VND, BB non-refundable, VAT on
  top. `rooms=6` at Sông Hàn gives 5 × Deluxe + 1 × Family Suite. Warm searches take 90–155 ms end to end, including
  country-wide `vn`. These were curl timings, not the planned `EXPLAIN ANALYZE`.
- The seed's pricing block was re-run on the existing DB and added no rows.
- No commits. The rollback copy is in the session scratchpad
  (`rollback/spring-base-before-c-srch-01.tgz`), and git in `spring-base` was clean at `84b6586` before this work.
