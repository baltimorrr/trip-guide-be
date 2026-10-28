---
phase: 3
title: Filter counts endpoint and docs
status: completed
priority: P2
dependencies:
  - 2
---

# Phase 3: Filter counts endpoint and docs

## Overview

New public `GET /api/search/hotels/filters`: same parameters as `/api/search/hotels`, answering every filter option
with the number of hotels the list would show if that option were picked, plus the per-night price range in the
search's currency. One native query reuses phase 2's CTE chain and predicates. Then docs, the full suite and a local
run.

## Context

- Phase 2 outputs: `HotelFilterSql`, `FilterGroup`, the `hotel_rows` → `scoped_offers` → `available` chain
- `search/controller/SearchController.java`, `search/mapper/SearchMapper.java`, `common/config/SecurityConfig.java`
  (`GET /api/search/**` is already public)
- Docs to update: `spring-base/README.md` (endpoint table, "Search criteria", "Prices", "Known limitations"),
  `spring-base/USER_STORIES.md` §3, `spring-base/CLAUDE.md` ("What this is", "Hotel catalog and search", Tests)

## Requirements

- Functional
  - Count semantics as in `plan.md`: an OR group ignores its own selection; facilities and room features narrow the
    current result; thresholds count hotels at or above / within each value; every option is listed (0 included).
  - `priceRange`: min and max per-night price over the hotels matching every filter except price and selling in
    `currency`. Min rounds down and max rounds up to whole minor units. Null when there are none.
  - Option names come from the tables (board, accommodation type, amenities with their `group_name` as `category`,
    bed types) and from `CancellationOption`. Numeric options have no name; the client formats "4.5+" or "< 1 km".
  - Errors as for `/hotels` (400 invalid criteria, 404 unknown place or hotel).
- Non-functional: one statement for all counts, plus the small option-list JPQL. Within the 2 s NFR on the seed;
  record the timings.

## Architecture

**Count query** = phase 2's chain with two swaps:

1. `candidate_hotels` keeps every hotel in the place (no hotel predicates), because a hotel failing one group must
   still count for that group's options.
2. `scoped_offers` adds one branch per room-filter option, each with the *other* room predicates:

```sql
select 'ALL', '', o.* from offers o where <offerPredicates(all)>
union all select 'BOARD_TYPE', o.board_type, o.* from offers o where <offerPredicates(except BOARD_TYPE)>
union all select 'CANCELLATION', o.cancellation, o.* from offers o
          where o.cancellation is not null and <offerPredicates(except CANCELLATION)>
union all select 'BED_TYPE', b.code, o.* from offers o cross join unnest(o.bed_types) b(code)
          where <offerPredicates(except BED_TYPE)>
union all select 'ROOM_FEATURES', f.code, o.* from offers o cross join unnest(o.room_features) f(code)
          where <offerPredicates(all)>                       -- AND group: narrow the current result
```

The scoped fill (`cheapest_rates` → `available`) then prices each option's offers independently, so a count respects
the price range exactly as the list would. The final select is a `union all` of `(filter, value, hotels, min_total,
max_total)` rows, joining `available v` to `hotel_rows c`:

| Rows | Condition | Grouping |
|------|-----------|----------|
| STARS | `v.filter = 'ALL'` and price and `hotelPredicates(except STARS)` and stars not null | `c.star_rating` |
| GOOGLE_RATING / USER_RATING | same pattern, `cross join unnest(array[3.0,3.5,4.0,4.5])` / `[6,7,8,9]` `t`, `rating >= t` | `t` |
| ACCOMMODATION_TYPE | `hotelPredicates(except ACCOMMODATION_TYPE)` | `c.accommodation_type` |
| FACILITIES | `hotelPredicates(all)`, `cross join unnest(c.facilities)` | amenity code |
| DISTANCE | `unnest(array[1000,3000,5000,10000])` `t`, `distance <= t`, `hotelPredicates(except DISTANCE)` | `t` |
| room-filter groups | `v.filter <> 'ALL'` and price and `hotelPredicates(all)` | `v.filter, v.value` |
| PRICE | `v.filter = 'ALL'` and `v.currency = :priceCurrency` and `hotelPredicates(all)` (no price predicate) | one row: `count(*), min(v.total), max(v.total)` |

The numeric option values are Java constants in one place (`FilterGroup` or a `SearchFilterOptions` holder),
inlined as literals (never request data). The response lists are built from the same constants.

**Repository**
- `HotelRepositoryCustom.findFilterCountsInDestination(destinationId, stay, filter)` /
  `findFilterCountsForHotel(hotelId, stay, filter)` → `List<FilterCountView>`
  (`String filter, String value, long hotels, Long minTotalMinor, Long maxTotalMinor`).
- `HotelRepositoryCustom.findFilterOptions()` → `List<FilterOptionView>` (`FilterGroup filter, String value,
  String name, String category`): four short JPQL queries over `BoardType`, `AccommodationType`, `Amenity` (scope →
  FACILITIES / ROOM_FEATURES, ordered by group then name) and `BedType` (by id).

**Service and API**
- `SearchService.filterCounts(HotelSearchRequest)` reuses phase 2's validation, currency resolution and filter
  building (extract one private method shared with `searchHotels`), then merges options with counts (missing → 0).
  Price per night = total ÷ nights, then `Money.ofMinor`.
- DTOs (`search/dto`): `HotelFilterCounts(String currency, PriceRange priceRange, List<FilterOption> stars,
  minGoogleRating, minUserRating, accommodationTypes, facilities, maxDistanceM, boardTypes, cancellation, bedTypes,
  roomFeatures)`, `FilterOption(String value, String name, String category, long hotels)`,
  `PriceRange(Money min, Money max)`. The mapper builds them.
- `SearchController`: `@GetMapping("/hotels/filters")`, `@SecurityRequirements`, `@Valid @ParameterObject
  HotelSearchRequest`. The Javadoc states the count semantics.

## Related Code Files

- Create: `hotel/repository/FilterCountView.java`, `FilterOptionView.java`
- Create: `search/dto/HotelFilterCounts.java`, `FilterOption.java`, `PriceRange.java`
- Modify: `hotel/repository/HotelRepositoryCustom.java`, `impl/HotelRepositoryImpl.java`, `impl/HotelFilterSql.java`
- Modify: `search/services/SearchService.java`, `impl/SearchServiceImpl.java`, `search/mapper/SearchMapper.java`,
  `search/controller/SearchController.java`
- Create test: `search/controller/SearchControllerFilterCountsTest.java`
- Modify docs: `spring-base/README.md`, `spring-base/USER_STORIES.md`, `spring-base/CLAUDE.md`; this plan's `plan.md`
  (status, implementation notes)

## Implementation Steps

1. Add the views, the option-list JPQL and the count query (built from phase 2's fragments; no copied CTE text).
2. Service method, DTOs, mapper, controller endpoint.
3. `SearchControllerFilterCountsTest` (own city per test, own codes or known master codes, assert on own data only):
   - **OR group:** 3 hotels (4★, 4★, 5★): stars counts 4 → 2, 5 → 1, and stay the same with `stars=5` selected;
     adding `minUserRating=9`, met only by one 4★ hotel, changes them to 4 → 1, 5 → 0.
   - **AND group:** facilities: with `facilities=pool` selected, `wifi` counts only pool hotels that also have wifi.
   - **Room-filter options re-priced:** a hotel with RO 800k and BB 1,500k per night, with `maxPrice=1000000`, counts
     under RO but not under BB.
   - **Cancellation and bed options** count only hotels that can still sell the stay with that option.
   - **Consistency:** for each option of every group in one fixture city, `hotels` equals `page.totalElements` of
     `/api/search/hotels` with that option added (OR groups: its own selection replaced; AND groups and thresholds:
     added or set). This is the guard that the two SQL paths agree.
   - **Price range:** per night, in the search currency; ignores the price filter itself; a `USD` hotel is left out;
     null for a city with no available hotel.
   - **Options:** every board type, accommodation type, bed type and amenity of the right scope appears, 0 included,
     with names and categories. Hotel search by slug works (counts 0 or 1). 400 and 404 behave as for `/hotels`.
4. `./mvnw test -Dtest='Search*'`, then the full `./mvnw verify`.
5. Local run (port 8081, seed): curl Đà Nẵng with no filters, `boardTypes=BB`, `facilities=pool&sort=price`, and
   `/filters` with and without `maxPrice`. Record warm timings for both endpoints, plus `vn` country-wide, in
   `plan.md` "Implementation Notes".
6. Docs:
   - README: endpoint table row for `/api/search/hotels/filters`; a "Filters and sorting" subsection (parameter table,
     the two filter kinds, count semantics, single-currency rule, sort values and the review minimum, `curl`
     examples); "Prices" notes that room filters change the offer; "Known limitations": replace "no sorting, filters or
     currency conversion" with "no currency conversion (price filter and sort use one currency), no recommended
     sort", and note that the counts query prices every hotel in the place.
   - USER_STORIES: C-SRCH-02 `✅ Implemented`; C-SRCH-03 `🟡 Partly implemented` with "Left: recommended sort".
   - CLAUDE.md: add C-SRCH-02/03 and V6 to "What this is"; under "Hotel catalog and search" add the
     attribute/predicate/scope design, `HotelFilterSql` (render and bind together), `free_cancellation_until()` as the
     twin of `FreeCancellation` (change both), and the counts/list consistency test. Under Tests, add the new fixture
     helpers.
   - Verify every doc claim against the code (parameter names, defaults, config key), and keep README under the docs
     size limit.

## Success Criteria

- [x] `/api/search/hotels/filters` is public and returns every option group with counts and the price range.
- [x] The consistency test passes for every option in its fixture.
- [x] `./mvnw verify` passes. The local seed run works, and timings are recorded.
- [x] README, USER_STORIES and CLAUDE.md match the implemented behaviour. `plan.md` and the phases are marked completed
      with `ck plan check`.

## Risk Assessment

- **Two SQL paths diverging** (list vs counts): both are built from the same fragments, and the consistency test
  compares them option by option.
- **Cost:** the counts query prices every hotel in the place (no hotel-filter pruning), and the fill runs once per
  room-filter option. Pricing (`night_price()` per plan and night) dominates and runs once. The option scopes only
  multiply cheap rows after aggregation. If timings approach the NFR, the fallback is to prune hotels that fail two or
  more hotel groups. Record that in README rather than building it now.
- **`unnest` over arrays of codes** gives one row per code. A room type with two bed types counts once under each,
  which is the intended meaning ("has a king bed", "has a single bed").
- **Option lists from master tables** list amenities no hotel uses yet (count 0). The client may hide zero options;
  the API keeps them so a selected option never disappears from the list.
