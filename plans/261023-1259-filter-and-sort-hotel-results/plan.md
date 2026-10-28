---
title: 'Filter and sort hotel results (C-SRCH-02, C-SRCH-03)'
description: >-
  Add the twelve result filters of C-SRCH-02 to GET /api/search/hotels (hotel
  filters drop hotels; room filters re-pick the cheapest matching offer; price
  range per night), a per-option hotel count endpoint GET
  /api/search/hotels/filters, and the price / rating (minimum reviews) /
  distance sorts of C-SRCH-03. New V6 tables for facilities, room features and
  bed types, plus a SQL twin of the free-cancellation rule.
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

# Filter and sort hotel results (C-SRCH-02, C-SRCH-03)

## Overview

Sources: `spring-base/USER_STORIES.md` §3 (C-SRCH-02, C-SRCH-03), §10 (NFR: search under 2 s for 95% of requests);
`spring-base/DATABASE-SCHEMA.md` (`amenities`, `hotel_amenities`, `room_type_amenities`, `bed_types`,
`room_type_beds`). Builds on the completed [C-SRCH-01 plan](../261023-1215-hotel-result-list-cheapest-offer/plan.md):
`GET /api/search/hotels` prices every available hotel with one native SQL chain
(`HotelRepositoryImpl.PRICED_AVAILABILITY`), pages in fixed "user rating" order and ignores `sort`.

All other plans in `plans/` are completed. No cross-plan dependency.

## User Decisions (2026-10-23, do not reverse silently)

| Decision | Choice |
|----------|--------|
| Scope | **Hold**: both stories as written, including a hotel count for every filter option |
| Mixed currencies | **Single-currency rule** until C-SRCH-05: a `currency` param (default: the most common selling currency of the searched place). The price filter matches only hotels selling in it; the price sort puts other currencies last. No exchange-rate tables |
| "Recommended" sort | **Not implemented now.** Sorts are `price`, `rating`, `distance`; with no `sort`, today's order stays. C-SRCH-03 ends 🟡 |
| Git | No commits or staging by Claude (standing preference) |

## Planner Defaults (changeable)

- **Two kinds of filter.** Hotel filters (name, stars, Google rating, user rating, accommodation type, facilities,
  distance) remove hotels. Room filters (board type, cancellation, bed type, room features) limit which rate plans
  and room types the offer may use. The card then shows the *cheapest matching* offer (still cheapest-mix for
  multi-room). Price range checks that offer.
- **Price range is per night for the whole offer** (all rooms): `minPrice`/`maxPrice` in major units of `currency`,
  inclusive, compared as `total >= minPrice × nights` (exact, no rounding).
- **Counts per option** (`GET /api/search/hotels/filters`, same query parameters): the number of hotels the list would
  show if that option were picked. Within an OR group (stars, accommodation type, board, cancellation, bed type) the
  group's own selection is ignored. Facilities and room features are AND groups: their count is the current result
  narrowed by that option. Thresholds (Google/user rating, distance) are single-value. Every option is listed, even
  with 0. Each list is named after the query parameter it fills.
- **Option values:** stars 1–5; Google rating 3.0 / 3.5 / 4.0 / 4.5; user rating 6 / 7 / 8 / 9; distance 1 / 3 / 5 /
  10 km; board types, accommodation types, bed types and amenities from their tables.
- **Cancellation filter:** `FREE` = free cancellation is still possible for this stay (deadline after the request's
  `now`); `NON_REFUNDABLE` = policy not refundable. A refundable plan whose free window has passed matches neither.
  The deadline rule gets a SQL twin `free_cancellation_until()` of `FreeCancellation.until` (same "change both"
  pattern as `HotelJpql`/`HotelSql`), guarded by a parity test.
- **Sort:** the Pageable `sort` param, one of `price` (cheapest first), `rating` (hotels with at least
  `app.search.rating-sort-min-reviews` = 10 reviews first, then by user rating), `distance` (nearest first, unknown
  last). Case-insensitive, direction ignored. Unknown or several values → 400. No `sort` → today's order.
- Unknown codes in list filters match nothing (no 400, no lookup). Ranges, enums, list sizes and the currency
  format are validated (400). `minPrice > maxPrice` → 400. Unknown ISO currency → 400.
- Hotels with no star rating, rating or distance never match those filters. The user-rating filter has no review
  minimum (the story asks for one only when sorting).
- Facilities = `amenities.scope = 'HOTEL'`, room features = `'ROOM'`. `hotel_amenities.is_free` is stored, not filtered.
- Amenity and bed-type master rows come with V6 (like board types). Hotel and room assignments come only from the demo
  seed and test fixtures (no write API, as for rates).

## Phases

| Phase | Name | Status | Depends on |
|-------|------|--------|------------|
| 1 | [Filter tables and free-cancellation SQL](./phase-01-filter-tables-and-free-cancellation-sql.md) | Completed | — |
| 2 | [Filtered and sorted results](./phase-02-filtered-and-sorted-results.md) | Completed | 1 |
| 3 | [Filter counts endpoint and docs](./phase-03-filter-counts-endpoint-and-docs.md) | Completed | 2 |

Strictly sequential: phase 2 reads the V6 tables and function; phase 3 reuses phase 2's SQL fragments and predicates.

## API Change

`GET /api/search/hotels` gains filter and sort parameters (same response shape):

```text
...&name=beach&stars=4&stars=5&minGoogleRating=4.0&minUserRating=8&accommodationTypes=resort
   &facilities=pool&facilities=wifi&maxDistanceM=3000&boardTypes=BB&cancellation=FREE&bedTypes=king
   &roomFeatures=balcony&minPrice=500000&maxPrice=3000000&currency=VND&sort=price&page=0&size=20
```

New `GET /api/search/hotels/filters` (same parameters, no paging or sort):

```json
{
  "currency": "VND",
  "priceRange": {"min": {"amount": 1350000, "currency": "VND"}, "max": {"amount": 4200000, "currency": "VND"}},
  "stars": [{"value": "5", "name": null, "category": null, "hotels": 12}, "..."],
  "minGoogleRating": [{"value": "4.5", "name": null, "category": null, "hotels": 7}, "..."],
  "minUserRating": ["..."], "accommodationTypes": [{"value": "resort", "name": "Resort", "category": null, "hotels": 3}],
  "facilities": [{"value": "pool", "name": "Swimming pool", "category": "Wellness", "hotels": 9}],
  "maxDistanceM": [{"value": "1000", "name": null, "category": null, "hotels": 4}],
  "boardTypes": ["..."], "cancellation": [{"value": "FREE", "name": "Free cancellation", "category": null, "hotels": 15}],
  "bedTypes": ["..."], "roomFeatures": ["..."]
}
```

## Acceptance Criteria

- [x] Every C-SRCH-02 filter works on `/api/search/hotels`, alone and combined (AND across filters, OR inside the
      OR groups, AND inside facilities and room features).
- [x] With room filters, each card's offer is the cheapest offer that uses only matching rate plans and room types.
      A hotel without one is hidden. `page.totalElements` stays the result count.
- [x] Price range is per night of the whole offer, in `currency`. Hotels selling in another currency don't match it.
- [x] `/api/search/hotels/filters` lists every option with its hotel count. For each option, the count equals
      `page.totalElements` of `/api/search/hotels` with that option added (tested).
- [x] `sort=price|rating|distance` orders results. The rating sort puts hotels below the review minimum after the
      others. Paging is stable. Unknown sort → 400. No sort → the C-SRCH-01 order.
- [x] Every filter is a GET parameter, and the counts response names each list after its parameter (URL-friendly,
      shareable).
- [x] All existing tests pass unchanged. `./mvnw verify` passes. A local run with the seed filters, counts and sorts
      Đà Nẵng results; timings recorded.
- [x] README, USER_STORIES (C-SRCH-02 ✅, C-SRCH-03 🟡 recommended pending), CLAUDE.md updated.

## Out of Scope

"Recommended" sort, currency conversion and exchange rates (C-SRCH-05), map (C-SRCH-06), no-results suggestions
(C-SRCH-07), filter chips and URL handling in a client (no frontend in this repo), owner/admin APIs for amenities, beds
and master data, price histograms, cached or precomputed counts.

## Open Questions

None blocking. Worth a later product check: the rating-sort review minimum (10), and whether a refundable plan whose
free window has passed should count as "non-refundable".

## Rollback

No commits. Before phase 1, back up `spring-base/` to the session scratchpad (`rollback/spring-base-before-c-srch-02-03.tgz`).
Git in `spring-base` is clean at `dd2ac87`. V6 is new; dropping the local DB (`docker compose down -v`) undoes it locally.

## Implementation Notes (2026-10-23)

- Done as planned. `./mvnw verify` passed: 208 tests (180 before), including the new `SearchControllerFilterTest` (7),
  `SearchControllerSortTest` (5), `SearchControllerFilterCountsTest` (8: one of them checks that every option's count
  equals `/hotels` totalElements with that option picked, over three base filter sets), `FreeCancellationSqlTest` (4)
  and `HotelSortTest` (3). The pre-existing search tests passed unchanged after the SQL refactor and before any filter
  existed.
- Found while testing: Hibernate expands a list parameter inside `array[...]` into a row value (`array[(?, ?)]`), so
  the planned `@>` / `&&` predicates matched nothing with two values. Array columns are now matched with
  `unnest(...) ... in (:list)` (bed types: `exists`; facilities and room features: a count equal to the number of
  values). Hibernate also rejects binding a parameter the SQL doesn't use, so `results` always selects
  `:priceCurrency`. Both are noted in `spring-base/CLAUDE.md`.
- Additions beyond the phase files: `HotelOrder` (sort plus the configured review minimum, so the repository doesn't
  read search config), `Money.toMinor`, and a short-circuit when a destination has no listed hotel and the request
  names no currency (empty page / zero counts, `currency: null`).
- Local run (seed, port 8081): filters, counts and sorts behave as expected for Đà Nẵng and `vn`. Warm timings were
  0.06–0.19 s for `/hotels` and 0.08–0.13 s for `/hotels/filters`, but the seed has only 8 hotels, so this proves
  nothing about scale (README "Known limitations").
- No commits. The rollback copy is in the session scratchpad (`rollback/spring-base-before-c-srch-02-03.tgz`), and git
  in `spring-base` was clean at `dd2ac87` before this work.
