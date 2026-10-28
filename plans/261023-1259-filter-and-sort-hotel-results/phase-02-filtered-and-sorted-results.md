---
phase: 2
title: Filtered and sorted results
status: completed
priority: P1
dependencies:
  - 1
---

# Phase 2: Filtered and sorted results

## Overview

`GET /api/search/hotels` accepts the C-SRCH-02 filters and the C-SRCH-03 sorts. The priced-availability SQL is
reshaped so phase 3 can reuse it for counts without copying it: hotel and offer *attributes* are computed once, and
filters become predicates over them, rendered by Java only when active. The cheapest-mix fill gains a
`(filter, value)` scope: results use one scope, and phase 3 uses one per option.

## Context

- `hotel/repository/impl/HotelRepositoryImpl.java` (`CANDIDATES_IN_DESTINATION`, `CANDIDATE_HOTEL`,
  `PRICED_AVAILABILITY`, `PAGE_LINES`, `COUNT_AVAILABLE`, `bind`)
- `hotel/repository/HotelSql.java`, `HotelJpql.java`, `StayRequirement.java`, `HotelRepositoryCustom.java`
- `search/dto/HotelSearchRequest.java`, `search/services/impl/SearchServiceImpl.java`, `search/config/SearchProperties.java`,
  `search/controller/SearchController.java`, `common/dto/Money.java`
- `CLAUDE.md`: native SQL only where JPQL can't, with a comment saying why; LIKE needs `escape '\'`; `\s` at
  text-block line ends; `PageResponse.from(page)`

## Requirements

- Functional
  - New GET params (all optional): `name`, `stars` (1–5, repeated), `minGoogleRating` (0–5), `minUserRating` (0–10),
    `accommodationTypes`, `facilities`, `maxDistanceM` (> 0), `boardTypes`, `cancellation` (`FREE`, `NON_REFUNDABLE`),
    `bedTypes`, `roomFeatures`, `minPrice`, `maxPrice` (≥ 0, major units), `currency` (`^[A-Z]{3}$`), `sort`.
  - Semantics as in `plan.md` "Planner Defaults". Room filters change the offer, price checks the resulting offer
    per night, and the single-currency rule applies.
  - `sort`: `price` → `(v.currency <> :priceCurrency), v.currency, v.total, h.id`; `rating` →
    `(h.user_rating_count >= :minReviews) desc, h.user_rating_avg desc nulls last, h.user_rating_count desc, h.id`;
    `distance` → `h.distance_to_center_m nulls last, h.id`; none → `h.user_rating_avg desc nulls last,
    h.user_rating_count desc, h.id` (unchanged).
- Non-functional: with no filters, results, offers and order are identical to today (the existing tests prove it).
  No extra round trip apart from one small currency lookup. Search stays stateless.

## Architecture

**Repository inputs** (`hotel/repository`, beside `StayRequirement`):

```java
/** Active result filters; empty lists and nulls mean "any". Codes are matched as given. */
public record HotelFilter(String nameQuery /* LIKE-escaped */, Set<Integer> stars, BigDecimal minGoogleRating,
        BigDecimal minUserRating, Set<String> accommodationTypes, Set<String> facilities, Integer maxDistanceM,
        Set<String> boardTypes, Set<String> cancellation, Set<String> bedTypes, Set<String> roomFeatures,
        Long minNightMinor, Long maxNightMinor, String priceCurrency /* never null */, Instant now) { }

public enum HotelSort { PRICE, RATING, DISTANCE }   // null = the default order

/** The filter groups that have options, and the alias each predicate reads. */
public enum FilterGroup { STARS, GOOGLE_RATING, USER_RATING, ACCOMMODATION_TYPE, FACILITIES, DISTANCE,   // c.
                          BOARD_TYPE, CANCELLATION, BED_TYPE, ROOM_FEATURES }                         // o.
```

**SQL fragments** (`hotel/repository/impl/HotelFilterSql.java`, package-private). This keeps
`HotelRepositoryImpl` readable and keeps each fragment next to the parameters it binds. It renders predicates over
fixed aliases and collects their parameters in the same call, so the SQL text and the bound parameters can't drift:

| Group | Predicate (rendered only when active) |
|-------|---------------------------------------|
| name (no options; inside `hotel_rows`) | `norm(h.name) like concat('%', norm(:name), '%') escape '\'` |
| STARS | `c.star_rating in (:stars)` |
| GOOGLE_RATING / USER_RATING | `c.google_rating >= :minGoogleRating` / `c.user_rating_avg >= :minUserRating` |
| ACCOMMODATION_TYPE | `c.accommodation_type in (:accommodationTypes)` |
| FACILITIES | `c.facilities @> cast(array[:facilities] as text[])` → implemented as a count of `unnest(c.facilities)` codes `in (:facilities)` (see plan Implementation Notes) |
| DISTANCE | `c.distance_to_center_m <= :maxDistanceM` |
| BOARD_TYPE / CANCELLATION | `o.board_type in (:boardTypes)` / `o.cancellation in (:cancellation)` |
| BED_TYPE | `o.bed_types && cast(array[:bedTypes] as text[])` → implemented as `exists (... unnest(o.bed_types) ... in (:bedTypes))` |
| ROOM_FEATURES | `o.room_features @> cast(array[:roomFeatures] as text[])` → implemented like FACILITIES |
| price (on `v` = a scoped available offer) | `v.currency = :priceCurrency and v.total >= :minTotal and v.total <= :maxTotal` (`minTotal = minNightMinor × nights`; each bound only when given) |

API sketch: `hotelPredicates(filter, except)` / `offerPredicates(filter, except)` return `"true"` or the `and`-joined
predicates without the excepted group. `price(filter, stay)` and `sort(sort)` work the same way. All of them add to one
`Map<String, Object> params`, and the impl binds that map after building the query text.

**Query shape** (all CTEs written once; main query shown, phase 3 swaps two pieces):

```text
hotel_rows        h + address + accommodation type, listed, inside the place (or = the hotel), [name predicate];
                  columns: id, currency, star_rating, google_rating, user_rating_avg, distance_to_center_m,
                  accommodation_type (t.code), facilities = array(select am.code::text from hotel_amenities ha
                  join amenities am ... where ha.hotel_id = h.id and am.scope = 'HOTEL')
candidate_hotels  select c.id from hotel_rows c where <hotelPredicates(all)>          ← phase 3: no where
nights            unchanged
free_rooms        unchanged + bed_types = array(bed codes), room_features = array(ROOM amenity codes)
rate_offers       unchanged pricing/having, + rp.board_type_id, rp.cancellation_policy_id carried through
offers (alias o)  rate_offers + board_type (b.code), bed_types, room_features,
                  cancellation = case when free_cancellation_until(cp.id, :checkIn, h.check_in_from, h.timezone) > :now
                                      then 'FREE' when not cp.is_refundable then 'NON_REFUNDABLE' end
scoped_offers     select 'ALL' as filter, '' as value, o.* from offers o where <offerPredicates(all)>  ← phase 3: + one branch per option
cheapest_rates    distinct on (filter, value, room_type_id) ... order by filter, value, room_type_id, stay_price, rate_plan_id
filled / picked   partition by (filter, value, hotel_id), else unchanged
available (v)     group by filter, value, hotel_id having sum(quantity) >= :rooms;
                  total = sum(quantity * stay_price), currency = min(currency)
results           select v.* from available v where v.filter = 'ALL' and <price>
page              row_number() over (order by <sort>) from results v join hotels h ... limit/offset
lines / count     picked p join page (p.filter = 'ALL') ... ; select count(*) from results
```

Why the shape: `free_rooms`, `rate_offers` and the fill keep their C-SRCH-01 meaning. Filters only *narrow*
`candidate_hotels` (pruning before pricing) and `scoped_offers`. The offer stays the cheapest mix of what is left. The
class Javadoc of `HotelRepositoryImpl` and the CTE list comment are updated to match.

**Service** (`SearchServiceImpl.searchHotels`):

1. Validate the stay as today. Capture `Instant now` once; the mapper's free-cancellation check reuses it (today it
   reads its own `Instant.now()` later).
2. Resolve the location as today, plus its price currency: request `currency`, else
   `hotels.findMainCurrencyInDestination(id)` (new JPQL: listed hotels inside the destination, `group by h.currency
   order by count(h) desc, h.currency`, first row), else the hotel's own `currency` for a hotel search. Unknown ISO
   code → 400 (`Currency.getInstance` throws `IllegalArgumentException`; catch it and throw `BusinessException`).
3. Build `HotelFilter`: dedupe lists into sets, LIKE-escape `name` (existing `escapeLike`), convert
   `minPrice`/`maxPrice` to minor units with the currency's ISO digits (`movePointRight`, `HALF_UP`), and reject
   `minPrice > maxPrice` with 400.
4. `HotelSort.from(pageable.getSort())`: unsorted → null; one order whose property matches a value case-insensitively
   → that value; otherwise 400 `"Sort by one of: price, rating, distance"`.
5. Call the repository with `(locationId, stay, filter, sort, pageable)`. Card loading and mapping are unchanged.

`SearchProperties` gains `@Positive int ratingSortMinReviews` (`app.search.rating-sort-min-reviews: 10`).
`SearchMapper.toOffer` takes the captured `now` as it already does.

## Related Code Files

- Create: `hotel/repository/HotelFilter.java`, `HotelSort.java`, `FilterGroup.java`; `hotel/repository/impl/HotelFilterSql.java`
- Create: `search/dto/CancellationOption.java` (enum `FREE`, `NON_REFUNDABLE`, with display names used in phase 3)
- Modify: `hotel/repository/HotelRepositoryCustom.java`, `impl/HotelRepositoryImpl.java`
- Modify: `search/dto/HotelSearchRequest.java` (new components with Bean Validation, list `@Size(max = 20)`, element
  `@Size`/`@NotBlank`), `search/services/SearchService.java` (Javadoc), `impl/SearchServiceImpl.java`,
  `search/config/SearchProperties.java`, `src/main/resources/application.yml`, `search/controller/SearchController.java`
  (Javadoc: filters and `sort`)
- Modify: `src/test/resources/application-test.yml` if it repeats `app.search` (check)
- Create tests: `search/controller/SearchControllerFilterTest.java`, `SearchControllerSortTest.java`,
  `hotel/repository/HotelSortTest.java` (unit test of `HotelSort.from`, no Docker)

## Implementation Steps

1. Refactor first, behaviour unchanged: introduce `hotel_rows`/`offers`/`scoped_offers` with the single `'ALL'` scope
   and no predicates. Run `SearchControllerTest` and `SearchControllerPricingTest`; both must pass untouched.
2. Add `HotelFilter`, `FilterGroup`, `HotelFilterSql` and the price/sort rendering. Change the repository signatures
   and update the `HotelRepositoryCustom` Javadoc ("order comes from `HotelSort`; the Pageable's sort is ignored").
3. Extend `HotelSearchRequest`, `SearchProperties` and the service (steps above). Add the currency lookup JPQL to
   `HotelRepositoryImpl` (reuse `HotelJpql.HOTEL_LISTED` and `ADDRESS_IN_DESTINATION`).
4. Tests (own hotels in a unique city each; assert only on own slugs):
   - **Hotel filters:** each one excludes a non-matching hotel: name (accent-insensitive contains; `%` matches only
     itself), stars OR, Google min, user min, type OR, facilities AND, distance (null distance excluded).
   - **Board:** a hotel with RO 900k and BB 1,000k shows BB 1,000k under `boardTypes=BB`. A hotel without BB is hidden.
   - **Cancellation:** `FREE` keeps a refundable plan with a future deadline, and drops non-refundable plans and
     refundable plans whose window has passed (check-in tomorrow, 48 h rule). `NON_REFUNDABLE` is the reverse.
     `FREE&NON_REFUNDABLE` keeps both.
   - **Bed type** OR and **room features** AND: a 2-room search where only one matching room type has free rooms
     shows the matching mix, and a hotel that only fits with a non-matching type is hidden.
   - **Price:** per night, boundaries inclusive (2 nights, total 2,000k matches `maxPrice=1000000`). A `USD` hotel is
     dropped by a price filter in `VND` and kept without one.
   - **Combined:** two filters from different groups narrow results; two values in one OR group widen them.
   - **Sort:** `price` ascending, with a `USD` hotel after the VND ones; `rating` puts a 9.8 / 3-review hotel after an
     8.0 / 50-review hotel; `distance` nulls last; page 2 continues page 1 with no overlap; `sort=PRICE` works;
     `sort=recommended` → 400; `sort=price&sort=rating` → 400.
   - **Validation:** `stars=6`, `minUserRating=11`, `minPrice > maxPrice`, `currency=vnd`, `currency=XXZ` (not ISO),
     `cancellation=SOMETIMES` → 400 problem+json, without echoing the rejected value.
5. `./mvnw test -Dtest='Search*'`, then the full `./mvnw verify`.

## Success Criteria

- [x] All Search tests pass. The pre-existing ones pass unchanged.
- [x] Each filter, combination, sort and validation case above has a test.
- [x] With no filters and no sort, the SQL returns the same rows as before (step 1 proves it).
- [x] No string from a request reaches SQL except as a bound parameter (only constant fragments and Java-side numbers
      are concatenated).

## Risk Assessment

- **Hibernate list parameters in native SQL:** `in (:x)` and `array[:x]` expand to one placeholder per element; an
  empty list would render invalid SQL. Mitigation: predicates are rendered only for non-empty sets. Step 4 covers one
  and several values.
- **Binding `Instant` as `timestamptz`** in a native query: verify with the cancellation tests; fall back to
  `OffsetDateTime` at UTC.
- **Plan quality:** room-filter predicates in `scoped_offers` may not be pushed below the `rate_offers` aggregation,
  so `night_price()` still runs for every plan of the filtered hotels. That is never worse than today. Hotel
  predicates prune before pricing.
- **Readability of a longer CTE chain:** keep one comment per CTE (existing style) and the alias contract
  (`c`, `o`, `v`, `h`) in the class Javadoc.
- **`HotelSearchRequest` grows to ~20 components:** acceptable for a GET parameter object. Keep the Javadoc `@param`
  list complete, because springdoc shows it.
