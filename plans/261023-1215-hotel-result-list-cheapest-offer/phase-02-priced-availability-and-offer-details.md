---
phase: 2
title: Priced availability and offer details
status: completed
priority: P2
dependencies:
  - 1
---

# Phase 2: Priced availability and offer details

## Overview

The native availability query now prices every fitting room type's rate plans for the stay. For each hotel it
picks the cheapest room mix and returns one page of hotels with their offer lines. JPQL then loads the names and
terms for those lines, the hotel photos and the tax flag. This phase adds the read-only entities those JPQL queries
need.

## Context

- `spring-base/src/main/java/com/company/project/hotel/repository/impl/HotelRepositoryImpl.java`: the current
  `CANDIDATES_*` + `AVAILABILITY` + `AVAILABLE_IDS`/`COUNT_AVAILABLE` text blocks, and `availableIds(...)`.
- `HotelRepositoryCustom`, `StayRequirement`, `HotelJpql`, `HotelSql`.
- Repository rules: `spring-base/CLAUDE.md` "Hotel catalog and search" (interface + `impl/XRepositoryImpl`, JPQL
  first, native only with a comment saying why, `select new` views) and memory `repository-queries-jpa-first`.
- Read-only entity pattern: `hotel/model/Hotel.java` (maps only the columns that queries read).

## Requirements

- Functional:
  - A room type contributes only rooms that an active rate plan of the hotel's currency can sell on every night:
    no `rate_plan_prices.is_closed` night, and `max(coalesce(pp.min_nights, rp.min_nights)) <= nights`.
  - Each room type uses its cheapest such rate plan (ties: lowest rate plan id). Rooms are filled from the cheapest
    room type upward (ties: lowest room type id) until `rooms` are covered. The hotel is available when they are.
  - Order and paging stay as today: user rating desc nulls last, rating count desc, id. The count runs only when
    the page can't tell the total.
- Non-functional: one native query for the page's lines plus at most one count query. All sums are cast to `bigint`
  (Postgres `sum(bigint)` is `numeric`).

## Architecture

New native CTE chain (replaces `AVAILABILITY` + `AVAILABLE_IDS`; the candidate CTEs are unchanged):

```sql
nights as (... unchanged ...),
free_rooms as (            -- unchanged filter; now also selects rt.id as room_type_id
    select rt.hotel_id, rt.id as room_type_id, min(...) as free ... group by rt.hotel_id, rt.id
),
-- Sellable rate plans of rooms with something free, priced for the whole stay (per room).
rate_offers as (
    select f.hotel_id, f.room_type_id, f.free, rp.id as rate_plan_id, rp.currency,
           cast(sum(night_price(rp.id, n.stay_date)) as bigint) as stay_price
    from free_rooms f
    join hotels h on h.id = f.hotel_id
    join rate_plans rp on rp.room_type_id = f.room_type_id and rp.is_active and rp.deleted_at is null
                      and rp.currency = h.currency
    cross join nights n
    left join rate_plan_prices pp on pp.rate_plan_id = rp.id and pp.stay_date = n.stay_date
    where f.free > 0
    group by f.hotel_id, f.room_type_id, f.free, rp.id, rp.currency
    having not bool_or(coalesce(pp.is_closed, false))
       and max(coalesce(pp.min_nights, rp.min_nights)) <= :nights
),
cheapest_rates as (       -- one rate plan per room type
    select distinct on (room_type_id) * from rate_offers order by room_type_id, stay_price, rate_plan_id
),
filled as (
    select c.*, sum(free) over (partition by hotel_id order by stay_price, room_type_id
                                rows unbounded preceding) - free as rooms_before
    from cheapest_rates c
),
picked as (
    select hotel_id, room_type_id, rate_plan_id, currency, stay_price,
           least(free, :rooms - rooms_before) as quantity
    from filled where rooms_before < :rooms
),
available as (
    select hotel_id from picked group by hotel_id having sum(quantity) >= :rooms
),
page as (
    select h.id, row_number() over (order by h.user_rating_avg desc nulls last, h.user_rating_count desc, h.id)
           as position
    from hotels h join available v on v.hotel_id = h.id
    order by position limit :limit offset :offset
)
select p.hotel_id, p.room_type_id, p.rate_plan_id, p.quantity, p.stay_price, p.currency
from picked p join page on page.id = p.hotel_id
order by page.position, p.stay_price, p.room_type_id
```

Count query: the same chain up to `available`, then `select count(*) from available`. Keep the fragments as text
blocks like today (`... + PRICED_AVAILABILITY + PAGE_LINES` / `+ COUNT_AVAILABLE`). The class javadoc says why this is
native: per-night rows (`generate_series`), `night_price()`, `distinct on` and a running sum.

Java side:

| Type | Kind | Content |
|------|------|---------|
| `hotel/repository/OfferLineView` | record | `hotelId, roomTypeId, ratePlanId, quantity, stayPriceMinor, currency` (one native row) |
| `hotel/repository/HotelOfferView` | record | `hotelId, currency, totalMinor, List<OfferLineView> lines`; built in the impl by grouping rows in order (`LinkedHashMap`) |
| `hotel/repository/HotelPhotoView` | record | `hotelId, url` |
| `hotel/repository/StayRequirement` | modify | add `nights()` (`ChronoUnit.DAYS.between`) |
| `hotel/model/RoomType` | entity (read-only) | `hotel` (lazy), `name` |
| `hotel/model/Hotel` | modify | map `currency`, `timezone`, `checkInFrom` (`LocalTime`, column `check_in_from`) |
| `rate/model/RatePlan` | entity (read-only) | `roomType`, `name`, `boardType`, `cancellationPolicy` (all lazy) |
| `rate/model/BoardType` | entity | `code`, `name` (reference data seeded by V5) |
| `rate/model/CancellationPolicy` | entity (read-only) | `name`, `refundable` (`is_refundable`) |
| `rate/model/CancellationPolicyRule` | entity (read-only) | `policy`, `hoursBeforeCheckIn`, `penaltyValue` |
| `rate/repository/RatePlanRepository` (+`Custom`, `impl/RatePlanRepositoryImpl`) | repository | `List<RateOfferView> findOfferDetails(Collection<Long> ratePlanIds)` |
| `rate/repository/RateOfferView` | record | `ratePlanId, ratePlanName, roomTypeName, boardCode, boardName, policyName, refundable, Integer penaltyFromHours, hotelTimezone, checkInFrom` |
| `tax/model/TaxRule` | entity (read-only) | `countryCode`, `included`, `active`, `validFrom`, `validTo` |
| `tax/repository/TaxRuleRepository` (+`Custom`, `impl/TaxRuleRepositoryImpl`) | repository | `Set<String> findCountriesWithTaxesOnTop(Collection<String> countryCodes, LocalDate firstNight, LocalDate lastNight)` |

`findOfferDetails` JPQL:

```
select new com.company.project.rate.repository.RateOfferView(
       rp.id, rp.name, rt.name, b.code, b.name, cp.name, cp.refundable,
       (select max(r.hoursBeforeCheckIn) from CancellationPolicyRule r where r.policy = cp and r.penaltyValue > 0),
       h.timezone, h.checkInFrom)
from RatePlan rp join rp.roomType rt join rt.hotel h join rp.boardType b join rp.cancellationPolicy cp
where rp.id in :ids
```

`findCountriesWithTaxesOnTop` JPQL: `select distinct t.countryCode from TaxRule t where t.countryCode in :countries
and t.active and t.included = false and (t.validFrom is null or t.validFrom <= :lastNight) and (t.validTo is null
or t.validTo >= :firstNight)`.

`findHotelPhotos` (added to `HotelRepositoryCustom`) JPQL: hotel photos only (`p.roomTypeId is null`) of `:ids`,
`order by p.hotel.id, p.cover desc, p.sortOrder, p.id`. The service caps them per hotel.

Repository signature change in `HotelRepositoryCustom`:
- `Page<HotelOfferView> findAvailableInDestination(long destinationId, StayRequirement stay, Pageable pageable)`
- `Page<HotelOfferView> findAvailableHotel(long hotelId, StayRequirement stay, Pageable pageable)`
- Update the javadocs to "... that can host the stay, each with its cheapest room mix".

## Related Code Files

- Create: `hotel/model/RoomType.java`; `hotel/repository/{OfferLineView,HotelOfferView,HotelPhotoView}.java`;
  `rate/model/{RatePlan,BoardType,CancellationPolicy,CancellationPolicyRule}.java`;
  `rate/repository/{RatePlanRepository,RatePlanRepositoryCustom,RateOfferView}.java`,
  `rate/repository/impl/RatePlanRepositoryImpl.java`; `tax/model/TaxRule.java`;
  `tax/repository/{TaxRuleRepository,TaxRuleRepositoryCustom}.java`, `tax/repository/impl/TaxRuleRepositoryImpl.java`
  (all under `spring-base/src/main/java/com/company/project/`)
- Modify: `hotel/model/Hotel.java`, `hotel/repository/{HotelRepositoryCustom,StayRequirement}.java`,
  `hotel/repository/impl/HotelRepositoryImpl.java`
- Modify (compile fix only, until phase 3 rewrites it): `search/services/impl/SearchServiceImpl.java`, which takes
  the hotel ids from `HotelOfferView` for now

## Implementation Steps

1. Entities first. `./mvnw test -Dtest=ProjectApplicationTest` proves the mappings validate against V5.
2. Change `StayRequirement` and the `HotelRepositoryCustom` signatures. Rewrite the native chain and its binding:
   add `nights`, `limit` and `offset` parameters; query with `Tuple.class` (or `Object[]`) and map to
   `OfferLineView` (`Number#longValue`/`intValue`, since drivers differ on int widths).
3. Build `Page<HotelOfferView>` with `PageableExecutionUtils.getPage(...)` and the count supplier.
4. Add `findHotelPhotos`, `RatePlanRepositoryImpl.findOfferDetails` and `TaxRuleRepositoryImpl.findCountriesWithTaxesOnTop`.
5. Make `SearchServiceImpl` compile against the new signature, so the existing search tests run unchanged against
   the priced query.
6. Run `SearchControllerTest` and `DestinationControllerTest`. Everything should pass apart from the
   `coverPhotoUrl` assertion, which phase 3 changes.

## Success Criteria

- [ ] App starts (`ddl-auto: validate` passes with the new entities).
- [ ] The existing availability tests pass on the priced query, using the fixtures' default rate plans.
- [ ] A room type without a sellable rate plan no longer makes its hotel available (covered by phase 3 tests).
- [ ] `EXPLAIN ANALYZE` of the page query on the local seed (Đà Nẵng, 2 nights) runs in milliseconds. Note the plan
      in the phase report.

## Risk Assessment

- **Numeric types from native queries**: `sum()` and `least()` widths vary. Cast in SQL, and convert through
  `Number` in Java.
- **`distinct on` + window functions** in Hibernate native queries pass through as-is. Parameters inside CTEs
  (`:limit`, `:offset`, `:nights`) bind like the existing ones. Cast `:checkIn`/`:checkOut` as today.
- **Function cost**: `night_price()` runs once per (rate plan × night) of the candidate hotels. That's fine for a
  city. A country-wide search over thousands of hotels may approach the 2 s p95 NFR: document it in README Known
  limitations rather than optimize now (YAGNI).
- **Read-only entities** mapping a subset of columns are the established pattern (`Hotel`). Owner write stories
  (O-ROOM-02) extend them later.
