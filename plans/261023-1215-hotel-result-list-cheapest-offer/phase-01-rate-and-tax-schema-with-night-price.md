---
phase: 1
title: Rate and tax schema with night price
status: completed
priority: P2
dependencies: []
---

# Phase 1: Rate and tax schema with night price

## Overview

Migration V5 adds the pricing tables from `DATABASE-SCHEMA.md` and the SQL function `night_price()`. This phase also
gives the local seed and the test fixtures rate plans, so the existing searches keep finding hotels once phase 2
requires a sellable rate.

## Context

- `spring-base/DATABASE-SCHEMA.md` lines ~568–695: `rate_plans`, `board_types`, `cancellation_policies`,
  `cancellation_policy_rules`, `rate_plan_prices`, `rate_rules`. Lines ~1000–1035: `tax_rules`.
- Conventions: `spring-base/CLAUDE.md` "Decided (2026-10-21)" and the Conventions section. Copy the standard columns
  from `V2__create_product_tables.sql`. Composite natural keys stay without id/version (`room_inventory` in V3).
- `norm()` in V3 shows the SQL-standard `return` body style for functions.

## Requirements

- Functional: tables and constraints as in the schema doc, translated to project conventions. `board_types` is
  seeded with RO/BB/HB/FB/AI. `night_price(plan_id, night)` returns the night's price in minor units.
- Non-functional: never edit V1–V4. The seed stays idempotent (it is a repeatable migration that re-runs when it
  changes). The test DB is shared and never rolled back.

## Architecture

`V5__create_rate_and_tax_tables.sql` (enum-like values are the Java enum names, UPPERCASE):

| Table | Columns (beyond standard id/version/created_at/updated_at) | Notes |
|-------|------------------------------------------------------------|-------|
| `board_types` | `code varchar(10)` unique, `name varchar(60)` | seed `RO` Room only, `BB` Breakfast included, `HB` Half board, `FB` Full board, `AI` All inclusive |
| `cancellation_policies` | `hotel_id` → hotels (cascade, null = platform template), `name varchar(150)`, `is_refundable boolean` | index on `hotel_id` |
| `cancellation_policy_rules` | `policy_id` → policies (cascade), `hours_before_check_in int >= 0`, `penalty_type` in `PERCENT/NIGHTS/FIXED`, `penalty_value numeric(12,2) >= 0` | unique `(policy_id, hours_before_check_in)` |
| `rate_plans` | `room_type_id` → room_types (cascade), `name varchar(150)`, `board_type_id` → board_types, `cancellation_policy_id` → policies, `base_price_minor bigint >= 0`, `currency varchar(3)` regex, `min_nights smallint >= 1 default 1`, `is_active boolean default true`, `deleted_at` | partial index `(room_type_id) where is_active and deleted_at is null` |
| `rate_plan_prices` | PK `(rate_plan_id, stay_date)`, `price_minor bigint >= 0`, `min_nights smallint >= 1` nullable, `is_closed boolean default false`, `updated_at default now()` | composite key, no id/version (like `room_inventory`) |
| `rate_rules` | `rate_plan_id` → rate_plans (cascade), `name varchar(100)`, `date_from`, `date_to` (inclusive), `days_of_week smallint[]` (ISO 1–7, null = every day), `adjustment` in `SET_PRICE/PERCENT/AMOUNT`, `amount_minor bigint`, `percent numeric(6,2)`, `priority smallint default 0`, `is_active boolean default true` | the doc's three CHECKs (dates, weekdays, adjustment/amount/percent combos); partial index `(rate_plan_id, date_from, date_to) where is_active` |
| `tax_rules` | `country_code varchar(2)` regex, `name varchar(100)`, `calc_type` in `PERCENT/PER_NIGHT/PER_BOOKING/PER_PERSON_NIGHT`, `value numeric(12,4) > 0`, `currency varchar(3)` regex nullable, `is_included boolean default false`, `applies_after_discount boolean default true`, `valid_from`, `valid_to`, `is_active boolean default true` | the doc's CHECKs (percent ≤ 100, `(calc_type = 'PERCENT') = (currency is null)`, valid range); partial index `(country_code) where is_active` |

No `currencies` table, and no FKs on the currency columns (the migration that creates `currencies` adds them,
as for `hotels.currency`).

`night_price()` (created after the tables; the `return` body records its dependencies on them):

```sql
-- Price of one night of a rate plan, in the plan's minor units. The only definition of a night's price:
-- 1. the rate_plan_prices row for that date (owner's override)
-- 2. the active rate_rules row with the highest priority (then newest) covering the date and its ISO weekday
-- 3. rate_plans.base_price_minor
-- Closed dates and minimum stays are not prices; callers check rate_plan_prices for those.
create function night_price(plan_id bigint, night date) returns bigint
    language sql stable parallel safe
    return coalesce(
        (select p.price_minor from rate_plan_prices p where p.rate_plan_id = plan_id and p.stay_date = night),
        (select case r.adjustment
                    when 'SET_PRICE' then r.amount_minor
                    when 'AMOUNT' then greatest(rp.base_price_minor + r.amount_minor, 0)
                    else round(rp.base_price_minor * (100 + r.percent) / 100)::bigint end
         from rate_rules r join rate_plans rp on rp.id = r.rate_plan_id
         where r.rate_plan_id = plan_id and r.is_active and night between r.date_from and r.date_to
           and (r.days_of_week is null or extract(isodow from night)::smallint = any (r.days_of_week))
         order by r.priority desc, r.id desc
         limit 1),
        (select rp.base_price_minor from rate_plans rp where rp.id = plan_id));
```

Parameter names (`plan_id`, `night`) must not collide with column names. Don't mark the function `strict`
(that blocks inlining).

## Related Code Files

- Create: `spring-base/src/main/resources/db/migration/V5__create_rate_and_tax_tables.sql`
- Modify: `spring-base/src/main/resources/db/seed/R__demo_catalog.sql`
- Modify: `spring-base/src/test/java/com/company/project/support/CatalogFixtures.java`

## Implementation Steps

1. Write V5 as in the table above. Use `bigint generated by default as identity` ids and `bigint` FKs, and name
   constraints `uk_<table>_<cols>` like V3.
2. Add `night_price()` after the tables, with the comment above.
3. Seed (append; every insert is skipped when its row exists):
   - Two platform policies (`hotel_id` null): "Free cancellation until 24 hours before check-in" (refundable; rule
     24h, `PERCENT` 100) and "Non-refundable" (not refundable; rule 100000h, `PERCENT` 100). Look them up by name +
     `hotel_id is null`.
   - Two rate plans for every demo room type without any: "Room only, free cancellation" (`RO`, free policy, base
     = 300000 × stars for Deluxe Double, 500000 × stars for Family Suite) and "Breakfast, non-refundable" (`BB`,
     non-refundable, 90% of that base). Currency `VND`.
   - One weekend rule on each RO plan without rules: "Weekend" +20% (`PERCENT` 20) on ISO days {5, 6}, from the seed
     date for 365 days.
   - One date override: the Mỹ Khê Beach Resort RO Deluxe plan at day 10 = 9,999,000 (shows override over rule).
   - Tax rule `VN` "VAT" `PERCENT` 10, `is_included = false`, no validity limits.
4. Fixtures (`CatalogFixtures`), test-only SQL as today:
   - `roomType(...)` keeps its signature and also inserts a default sellable rate plan (RO, 1,000,000 VND, a
     refundable policy with no penalty rules). Existing availability tests keep passing unchanged.
   - `bareRoomType(...)` inserts a room type with no rate plan.
   - `ratePlan(roomTypeId)` builder: `name`, `board(code)`, `price(minor)`, `currency`, `minNights`, `policy(id)`,
     `inactive()`, `create()` → id.
   - `cancellationPolicy(name, refundable)` → id; `cancellationRule(policyId, hours, penaltyType, value)`.
   - `nightPrice(ratePlanId, date, priceMinor, Integer minNights, boolean closed)`.
   - `rateRule(ratePlanId, from, to, Integer... isoDays)` builder with `percent`, `amount`, `setPrice`, `priority`.
   - `taxRule(countryCode, included, validFrom, validTo)`.
   - `roomPhoto(hotelId, roomTypeId, url)`.
   - Javadoc: tax rules apply to a whole country, so tax tests use their own user-assigned country codes
     (`XA`–`XZ`) and never `VN`.

## Success Criteria

- [ ] `./mvnw verify` passes. Flyway applies V5 on Testcontainers, and `ddl-auto: validate` is unaffected (no
      entities yet).
- [ ] Local run: the repeatable seed re-runs cleanly on an existing database and again with no changes (no duplicate
      plans, rules or policies).
- [ ] `select night_price(id, d)` on seed data returns override > rule > base as expected (manual psql check).

## Risk Assessment

- **Seed re-run on existing local DBs**: R__ runs after V5. Guard every insert with `not exists`, or the second
  run duplicates plans. Mitigation: run the app twice locally.
- **`round()` on numeric** rounds half away from zero; acceptable for minor units. Document it in the function
  comment if it changes.
- **Rollback**: V5 can't be un-applied by Flyway. Locally use `docker compose down -v` (drops the demo data too).
  Code rollback: `git checkout`/`git clean` in `spring-base` (the tree was clean at `84b6586`). The user does the
  git operations.
