---
title: >-
  Homepage search, last-search contract and popular destinations
  (C-HOME-01/02/03)
description: >-
  Add the first hotel-catalog tables to spring-base, a destination sync from
  hotel addresses, public location suggestions + availability search
  (C-HOME-01), a stateless GET contract the browser can replay for "last search"
  (C-HOME-02), and featured destinations with top hotels plus a small ADMIN
  slice to curate them (C-HOME-03, part of A-CONT-01).
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - search
blockedBy: []
blocks: []
created: 2026-10-21T00:00:00.000Z
---

# Homepage search, last-search contract and popular destinations (C-HOME-01/02/03)

## Overview

Sources: `spring-base/USER_STORIES.md` §2, `spring-base/DATABASE-SCHEMA.md` (hotel_catalog, rooms groups).
Builds on the completed plans [base project](../261019-2350-spring-boot-postgres-base-project/plan.md) and
[layered restructure](../261021-0219-restructure-layered-feature-packages/plan.md). The code has users/auth and the
`product` sample only; no hotel tables, no owner/admin catalog APIs, no frontend.

## User Decisions (do not reverse silently)

| Decision | Choice |
|----------|--------|
| Conventions | **Base conventions**: bigint identity `id`, `version`, `created_at`/`updated_at` (auditing), varchar + CHECK instead of Postgres enums/`char(n)`/`citext`. Table and column names follow `DATABASE-SCHEMA.md` |
| Search scope | **Availability only**: criteria validation + room capacity + free rooms every night. No prices, no rate plans (C-SRCH-01) |
| Data entry | **Local-only demo seed + ADMIN endpoints** to list destinations, set featured rank / image / display name, and run the sync now |
| Frontend | **Backend only**. C-HOME-02 = documented stateless GET contract; nothing stored server-side |
| Git | No commits or staging by Claude; rollback point is a scratchpad copy (user preference) |

## Planner Defaults (changeable)

- Tables with a composite natural key in the schema doc (`room_inventory`) keep it and skip `id`/`version`, like
  `user_roles` today. Every other new table gets the standard columns.
- Foreign keys to tables not created yet (`currencies`, …) are added by the migration that creates those tables.
- One SQL function `norm(text)` (unaccent + lowercase + collapsed spaces) is the only normalizer; Java never
  re-implements it. `destinations.normalized_name` is a generated column `norm(name)`.
- SQL-heavy reads and the sync use Spring `JdbcClient` in `repository/` classes; JPA stays for entity load/save.
  Reason: `norm()`, `generate_series`, lateral joins and dynamic location filters are plain SQL, and JdbcClient
  avoids Hibernate's null-parameter typing problems in native queries.
- Autocomplete uses `pg_trgm` GIN indexes with word-prefix `LIKE` (schema doc had a btree prefix index and a
  `to_tsvector` index; neither ignores accents or matches the 2nd word).
- Capacity: guests are split evenly over the rooms (`ceil(adults/rooms)`, `ceil(children/rooms)` per room).
  A hotel qualifies when the fitting room types together have `>= rooms` free on every night.
- `adults >= rooms` (every room needs an adult: schema has `cart_items.adults >= 1` per room). Story text says
  "guests >= rooms"; this is the stricter, bookable reading.
- Missing `room_inventory` row for a night = `room_types.total_rooms` free, nothing booked, open.
- "Today" for "check-in not in the past" = the date at UTC−12, so no traveller's local today is rejected.
- Popular destinations: featured (`featured_rank`) first; when none are featured, the top cities by hotel count.

## Phases

| Phase | Name | Status | Depends on |
|-------|------|--------|------------|
| 1 | [Catalog schema and SQL helpers](./phase-01-catalog-schema-and-sql-helpers.md) | Completed | — |
| 2 | [Destination sync](./phase-02-destination-sync.md) | Completed | 1 |
| 3 | [Location suggestions and hotel search](./phase-03-location-suggestions-and-hotel-search.md) | Completed | 2 |
| 4 | [Popular destinations and admin destination endpoints](./phase-04-popular-destinations-and-admin-destination-endpoints.md) | Completed | 2 |
| 5 | [Demo seed, docs and final verification](./phase-05-demo-seed-docs-and-final-verification.md) | Completed | 3, 4 |

Phases 3 and 4 touch disjoint packages (`search/` vs `destination/controller|dto|mapper`) but both edit
`SecurityConfig`; run them one after the other.

## API Added

| Method | Path | Access | Story |
|--------|------|--------|-------|
| GET | `/api/search/suggestions?q=&limit=` | public | C-HOME-01 |
| GET | `/api/search/hotels?destination=\|hotel=&checkIn=&checkOut=&rooms=&adults=&childAges=&page=&size=` | public | C-HOME-01, C-HOME-02 |
| GET | `/api/destinations/popular?limit=&hotels=` | public | C-HOME-03 |
| GET | `/api/destinations/{slug}` | public | C-HOME-01/02 (rebuild a shared or stored search) |
| GET | `/api/admin/destinations?q=&page=&size=` | ADMIN | A-CONT-01 slice |
| PUT | `/api/admin/destinations/{id}` | ADMIN | A-CONT-01 slice |
| POST | `/api/admin/destinations/sync` | ADMIN | A-CONT-01 slice |

## Acceptance Criteria

- [x] C-HOME-01: `q=da nang` suggests "Đà Nẵng"; countries, cities, districts, wards and hotel names are suggested;
      places with no active hotel are not.
- [x] C-HOME-01: check-in in the past, check-out ≤ check-in, > 30 nights, rooms outside 1..8, adults < rooms, child
      age outside 0..17 → 400 problem+json. Unknown destination/hotel slug → 404.
- [x] C-HOME-01: only hotels whose fitting room types have enough free rooms on every night are returned; all
      criteria are GET query parameters (shareable URL).
- [x] C-HOME-02: no search is stored server-side; README documents what the browser stores and how it replays it.
- [x] C-HOME-03: popular endpoint returns featured destinations in rank order with image, hotel count and up to N
      top hotels; the documented default search (tomorrow, 1 night, 2 adults) for a destination returns its hotels.
- [x] Destinations are created from active hotels' addresses; the sync never overwrites name, image or featured rank.
- [x] ADMIN can list destinations, set featured rank / image / name, and trigger the sync; USER gets 403.
- [x] `./mvnw verify` passes; local run with seed data answers the public endpoints.

## Out of Scope

Prices, rate plans, taxes, sorting/filters (C-SRCH-01..03), hotel detail (C-HOTEL-*), owner hotel CRUD (O-HOTEL-*),
image upload, destination delete/create by admin, the role model change (USER/ADMIN stays), any frontend.

## Open Questions

None blocking. Defaults above worth a later product check: the even-split capacity rule, `max_children` semantics
(phase 3), and `adults >= rooms`.

## Follow-up: JPA-first repositories (2026-10-21)

User decision after implementation: repository queries use Spring Data JPA / JPQL first, and native SQL only when
needed. This replaces the planner default "JdbcClient for SQL-heavy reads".
- **Now JPQL:**
  - suggestions, popular hotels, hotel cards, admin list, featured/top cities
  - the sync's hotel-count refresh
  - `norm()` is registered for JPQL by a Hibernate `FunctionContributor`.
- **Still native, via `@Query(nativeQuery = true)`:**
  - the per-night availability check, which returns ids only
  - the sync's bulk `ON CONFLICT` insert and its advisory lock
- New entities: `Hotel`, `Address`, `HotelPhoto`, `AccommodationType`.
- Found while doing it: Hibernate renders `escape ''`, so LIKE on typed text now says `escape '\'`. Covered by
  `SearchControllerTest#typedUnderscoreMatchesOnlyAnUnderscore`.

## Follow-up: repository interface + impl (2026-10-21)

User decision: repositories follow the services pattern.
- `XRepository` (Spring Data, derived queries only) extends `XRepositoryCustom`.
- `repository/impl/XRepositoryImpl` implements the hand-written queries with `EntityManager`: JPQL first, native
  only for availability and the sync upsert/lock.
- `@Query` annotations moved into the impls, and query projections became records (`select new`).
- `DestinationSyncRepository` is a plain interface plus a `@Repository` impl.
- `user/` and `product/` are unchanged (derived queries only).
