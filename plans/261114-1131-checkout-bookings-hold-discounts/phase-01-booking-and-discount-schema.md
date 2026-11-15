---
phase: 1
title: Booking and discount schema
status: completed
priority: P1
dependencies: []
---

# Phase 1: Booking and discount schema

## Overview

Migration V10 creates the booking and discount tables, the database's status rules and the foreign key from
`reviews.booking_id` that the 28 Oct decision deferred to this migration. This phase also adds the entities and
repositories, and switches the review fixture to real bookings.

## Requirements

- Functional:
  - The doc's tables, with this project's conventions: identity `bigint` ids, `version`, `created_at`/`updated_at`,
    `varchar` + CHECK holding the UPPERCASE Java enum names, `char(n)` → `varchar(n)` + a regex CHECK, and `citext`
    → `varchar` stored lowercased.
  - Status rules enforced by triggers; every status change logged.
- Non-functional:
  - `ddl-auto: validate` passes.
  - Existing tests stay green after the review fixture change.

## Architecture

**`V10__create_booking_and_discount_tables.sql`.** Tables are created in this order, because of the foreign keys:

1. **`discount_codes`**:
   - `code varchar(50) not null unique` with `check (code = lower(code))`
   - `description varchar(500)`
   - `discount_type` (`PERCENT`/`FIXED`), `value numeric(12,2) > 0`, `currency varchar(3)` with the regex CHECK
   - `max_discount_minor > 0`, `min_spend_minor >= 0`, both nullable
   - `starts_at`, `ends_at`
   - `usage_limit_total >= 1` and `usage_limit_per_user >= 1`, both nullable
   - `first_booking_only`; `funded_by` (`PLATFORM`/`HOTEL`/`SHARED`, default `PLATFORM`); `applies_to_all_hotels`
     (default true); `is_active`
   - `created_by` → users; `deleted_at`; the standard columns

   CHECKs: the doc's three (`value > 0`; `ends_at > starts_at`; percent ≤ 100, or fixed with a currency), plus a new
   one: `(min_spend_minor is null and max_discount_minor is null) or currency is not null`. Money limits need a
   currency; the doc is edited to match in phase 4.
2. **`discount_code_hotels`**: `(discount_code_id, hotel_id)` primary key; both FKs `on delete cascade`, as in the
   doc.
3. **`bookings`**: every doc column, with these types:
   - `reference varchar(12) unique`; `user_id` → users; `hotel_id` → hotels; `check_in`, `check_out`
   - `nights smallint generated always as (check_out - check_in) stored`
   - `status varchar(20) default 'PENDING'` with a CHECK of the seven names
   - `booker_name varchar(100)`, `booker_email varchar(254)` (lowercased), `booker_phone varchar(30)`, and
     `booker_country varchar(2)` (regex CHECK, nullable as in the doc)
   - `guest_name varchar(100)`, `special_requests varchar(1000)`, `estimated_arrival time`, `terms_accepted_at`
   - `currency`; the money columns, each `>= 0`; the display columns; `discount_code_id` → discount_codes
   - `commission_rate numeric(5,2)`, `commission_minor`
   - the lifecycle timestamps; `cancelled_by` → users; `cancel_reason varchar(500)`; `cancellation_fee_minor`
   - the standard columns

   CHECKs: the doc's six, plus `discount_minor <= subtotal_minor`. Indexes, as in the doc:
   - `(hotel_id, check_in)`
   - `(hotel_id, status, created_at)`
   - `(user_id, check_in desc)`
   - `pending_holds` on `hold_expires_at` where status is `PENDING`
4. **`booking_rooms`**:
   - `booking_id` → bookings `on delete cascade`; `room_type_id` → room_types and `rate_plan_id` → rate_plans, both
     without cascade (bookings keep pointing at them)
   - `room_name varchar(150)`, `board_type_code varchar(10)`, `cancellation_policy jsonb not null`
   - `adults >= 1`, `children >= 0`, `child_ages smallint[] default '{}'`, with the same CHECKs as `cart_items`
   - `lead_guest_name varchar(100)`, `total_minor >= 0`
   - `status` (`ACTIVE`/`CANCELLED`, default `ACTIVE`), `cancelled_at`
   - the standard columns

   Indexes on `booking_id` and `room_type_id`. The doc's unique `(id, room_type_id)` comes with `room_assignments`
   (O-BOOK-03).
5. **`booking_room_nights`**: `(booking_room_id, stay_date)` primary key, `price_minor >= 0`, an index on
   `stay_date`, and an FK `on delete cascade`. It keeps the doc's composite key without id or version, like
   `room_inventory`.
6. **`booking_taxes`**:
   - `booking_id` → bookings, cascade; `tax_rule_id` → tax_rules, nullable, no cascade
   - `name varchar(100)`, `is_included`, `amount_minor >= 0`
   - the standard columns, and an index on `booking_id`
7. **`booking_status_history`**:
   - `booking_id` → bookings, cascade; `from_status`, `to_status` (CHECKs of the names)
   - `changed_by` → users; `reason varchar(500)`
   - `created_at timestamptz not null default now()`, and an index on `booking_id`

   It has no version or `updated_at`: only the trigger writes rows, and they never change (like `wishlists`).
8. **`discount_redemptions`**:
   - `discount_code_id` → discount_codes
   - `booking_id` → bookings, unique and `on delete cascade`
   - `user_id` → users; `amount_minor > 0`
   - the standard columns, and an index on `(discount_code_id, user_id)`
9. `alter table reviews add constraint fk_reviews_booking_id foreign key (booking_id) references bookings (id)`.
10. The functions and triggers:

```sql
-- The booking lifecycle (USER_STORIES §7). A booking starts Pending; any other change is refused.
create function booking_status_check() returns trigger language plpgsql as $$
begin
    if tg_op = 'INSERT' then
        if new.status <> 'PENDING' then
            raise exception 'A booking starts as PENDING, not %', new.status using errcode = 'check_violation';
        end if;
    elsif new.status is distinct from old.status
        and (old.status, new.status) not in (('PENDING', 'CONFIRMED'), ('PENDING', 'CANCELLED'),
            ('PENDING', 'EXPIRED'), ('CONFIRMED', 'CHECKED_IN'), ('CONFIRMED', 'CANCELLED'),
            ('CONFIRMED', 'NO_SHOW'), ('CHECKED_IN', 'CHECKED_OUT')) then
        raise exception 'A booking can''t go from % to %', old.status, new.status using errcode = 'check_violation';
    end if;
    return new;
end $$;

-- Who and why come from transaction-local settings (set_config(..., true)); the booking's user creates it.
create function booking_status_log() returns trigger language plpgsql as $$
begin
    insert into booking_status_history (booking_id, from_status, to_status, changed_by, reason, created_at)
    values (new.id, case when tg_op = 'UPDATE' then old.status end, new.status,
            coalesce(nullif(current_setting('app.changed_by', true), '')::bigint,
                     case when tg_op = 'INSERT' then new.user_id end),
            nullif(current_setting('app.status_reason', true), ''), now());
    return null;
end $$;

create trigger bookings_status_check before insert or update of status on bookings
    for each row execute function booking_status_check();
create trigger bookings_status_log_insert after insert on bookings
    for each row execute function booking_status_log();
create trigger bookings_status_log_update after update of status on bookings
    for each row when (old.status is distinct from new.status) execute function booking_status_log();
```

`check_violation` makes Spring raise `DataIntegrityViolationException`, which the handler already turns into 409.

**Entities.**
- `booking/model`:
  - `BookingStatus` (the seven names)
  - `Booking` (BaseEntity), with `@OneToMany(mappedBy, cascade = ALL)` `rooms` and `taxes`. `estimatedArrival` uses
    `@JdbcType(LocalTimeJdbcType.class)`; `nights` is not mapped.
  - `BookingRoom` (BaseEntity), with `@ElementCollection` `nights` (`booking_room_nights`) of the `@Embeddable`
    `BookingNight(stayDate, priceMinor)`. `childAges` is `Short[]`, like `CartItem`. `cancellationPolicy` is a
    `CancellationTerms` record (name, refundable, rules) with `@JdbcTypeCode(SqlTypes.JSON)`.
  - `BookingTax` (BaseEntity)

  `create(...)` factories only; status methods come in phases 3 and 4.
- `discount/model`: `DiscountType`, `DiscountFunder`, `DiscountCode` (BaseEntity, mapped read-only:
  `discount_code_hotels` as an `@ElementCollection Set<Long> hotelIds`), and `DiscountRedemption` (BaseEntity).
- `booking_status_history` has no entity yet; C-MYBK-02 and O-BOOK-03 read it.

**Repositories.** `BookingRepository`, `DiscountCodeRepository` and `DiscountRedemptionRepository` (JpaRepository
plus `...Custom` where phases 2–4 add queries).

**Fixtures** (`CatalogFixtures`):
- `booking(userId, hotelId)` inserts a PENDING booking and walks it to CHECKED_OUT through the lifecycle with JDBC,
  so every trigger runs.
- `review(...)` uses it instead of a random `booking_id`.
- `discountCode(code)` is a builder: percent or fixed, currency, limits, dates, hotels, first booking only.

## Related Code Files

- Create:
  - `src/main/resources/db/migration/V10__create_booking_and_discount_tables.sql`
  - `booking/model/{Booking,BookingRoom,BookingNight,BookingTax,BookingStatus,CancellationTerms}.java`
  - `booking/repository/BookingRepository.java`
  - `discount/model/{DiscountCode,DiscountType,DiscountFunder,DiscountRedemption}.java`
  - `discount/repository/{DiscountCodeRepository,DiscountRedemptionRepository}.java`
  - test `booking/repository/BookingStatusRulesTest.java`, `booking/repository/BookingMappingTest.java`
- Modify: `src/test/.../support/CatalogFixtures.java` (`booking`, `review`, `discountCode`)

## Implementation Steps

1. Back up `spring-base` (without `target/`) to the scratchpad `rollback/spring-base-before-c-book.tgz`.
2. Write V10 as above. Check each column and CHECK against the doc, and the relationships section
   (`Ref: bookings...`, `discount_*`, `reviews.booking_id`).
3. Map the entities. The `jsonb` mapping must work with Hibernate 7.4 and Jackson 3. If no JSON format mapper is
   found, map `cancellationPolicy` as a `String` with `@JdbcTypeCode(SqlTypes.JSON)` and serialize it in the service
   with the app's `JsonMapper`, then note which one was used.
4. Add the repositories, then start the context (`./mvnw test -Dtest=BookingMappingTest`) to prove `validate`
   passes.
5. Change the fixtures, then run every test that uses `review(...)` (`ReviewControllerTest`, the hotel page tests).
6. Write `BookingStatusRulesTest` (JDBC):
   - an insert that isn't PENDING is refused
   - each lifecycle arrow is allowed and logged with `from_status`/`to_status`
   - PENDING → CHECKED_IN and CHECKED_IN → CANCELLED are refused
   - an update that leaves the status alone logs nothing
   - `set_config('app.status_reason', ...)` lands in `reason`
   - a review with an unknown `booking_id` is refused
7. Write `BookingMappingTest`: save a booking with two rooms, nights and a tax through the repository and read it
   back (ages, `jsonb` terms, arrival time, nights).

## Success Criteria

- [x] V10 applies on a fresh database and on the local one (`./mvnw spring-boot:run` with the demo seed).
- [x] Status rules and history are proven by `BookingStatusRulesTest`; the mapping round trip passes.
- [x] All tests that existed before are green with the new review fixture.

## Risk Assessment

- **First triggers in the project.** They are kept small, and their behaviour is pinned by tests. Hibernate updates
  every column, so `update of status` fires on any update; the `when` clause and the `is distinct from` test cover
  that.
- **JSON mapping.** The fallback is described in step 3.
- **Local databases with reviews** that point at no booking would fail the FK. The demo seed has none; if a dev
  database has some, delete them before starting.
