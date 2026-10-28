---
title: Hotel detail page (C-HOTEL-01/02/03/04/06/07)
description: >-
  Public hotel detail API by slug: overview with gallery, address and map
  position, grouped facilities, check-in/out times and policies; a dated room
  list with every sellable rate, rooms left, guest fit and a priced cancellation
  timeline per rate; and paged published reviews. One new migration (V8:
  hotel_policies, reviews). Search output stays unchanged.
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - hotel
  - reviews
blockedBy: []
blocks: []
created: 2026-10-28T00:00:00.000Z
---

# Hotel detail page (C-HOTEL-01/02/03/04/06/07)

## Overview

Sources: `spring-base/USER_STORIES.md` §4 (C-HOTEL-01, 02, 03, 04, 06, 07), §3 C-SRCH-01 ("clicking a card opens the
hotel detail page with the same search criteria"), §10 (time zones, SEO-readable URLs). `spring-base/DATABASE-SCHEMA.md`:
`hotels`, `addresses`, `hotel_photos`, `hotel_policies`, `amenities`/`hotel_amenities`, `room_types`,
`room_type_beds`, `room_type_amenities`, `rate_plans`, `cancellation_policies`/`_rules`, `reviews`.

Every other plan in `plans/` is completed, so nothing blocks this plan and it blocks nothing. Git in `spring-base` is
clean at `da7c4ae`.

| Endpoint (public GET) | Stories |
|---|---|
| `/api/hotels/{slug}` | 01 overview + gallery + address/map, 02 facilities, 03 policies |
| `/api/hotels/{slug}/rooms?checkIn&checkOut&rooms&adults&childAges` | 04 room list, 06 cancellation detail |
| `/api/hotels/{slug}/reviews?page&size&sort` | 07 reviews |

## User Decisions (2026-10-28, do not reverse silently)

| Decision | Choice |
|----------|--------|
| Scope | **Hold**: the six stories as written. No reviewer names, score breakdown, wishlist flag on the page, or search filters applied to the room list |
| Reviews vs missing `bookings` | **Exact schema**: `reviews.booking_id bigint not null unique`, its FK to `bookings` added by the migration that creates `bookings`. **No demo reviews** in the seed (fake booking ids would break that FK); locally the list stays empty until C-MYBK-05 writes reviews. Tests insert their own reviews with unique fake booking ids |
| Rooms that don't fit the guests | **Marked, not hidden**: returned with `fitsGuests: false` (plus their max adults/children/occupancy), listed after the fitting ones |
| Git | No commits or staging by Claude (standing preference) |

## Planner Defaults (changeable)

- Hotels are addressed by `slug` (search cards link by slug; readable URLs). A hotel that doesn't exist or isn't
  listed (`ACTIVE`, not deleted) answers 404 on all three endpoints.
- Overview needs no dates. Rooms need the search's stay parameters, checked by the same `SearchCriteria` rules (400).
  C-HOTEL-05 (change dates on the page) is then a client call to `/rooms` with new parameters.
- Address `formatted` = non-blank `line1, ward, district, city, country name`, joined with ", ". Province and the
  stored `addresses.formatted` aren't used. The country name comes from the COUNTRY destination row, as suggestion
  labels do. Before the sync has created that row, the country code is used instead.
- Gallery: every hotel photo, plus the room photos of room types on sale (`ACTIVE`, not deleted). Hotel photos come
  first, cover first, then by `sort_order` and id. Each has `category` and `caption`, and a room photo without a
  category reports `room`.
- Facilities: the hotel's `HOTEL`-scope amenities, grouped by `group_name` (groups and names alphabetical, as in the
  filter options), each with `free`.
- Policies: `checkInFrom`, `checkInUntil`, `checkOutUntil`, `minCheckInAge`, plus `hotel_policies` rows ordered by
  `sort_order` and id. Types are the schema's list as Java enum names: `CHILDREN`, `EXTRA_BED`, `PETS`, `SMOKING`,
  `DEPOSIT` (ID and deposit), `PAYMENT` (methods accepted at the property), `OTHER`. All are free text and
  read-only, since no owner write API exists yet.
- Rooms list room types that can sell the whole stay and have at least 1 room free every night. Each room type has
  every sellable rate plan, using the same rules as search. Sold-out room types and unsellable plans are hidden.
  `roomsLeft` is the exact fewest-free-rooms count, shared by all rates of that room type. The order is: fitting
  first, then cheapest rate, `sort_order`, id. Within a room type, rates go cheapest first.
- Prices are per room: `totalPrice` covers the stay exactly. `pricePerNight` is the average, rounded half up to minor
  units. `taxesIncluded` uses the search rule.
- Cancellation (C-HOTEL-06), per rate and per room:
  - `freeUntil` = `FreeCancellation.until`, unchanged.
  - `penalties` = the policy's rules as time tiers, listed in time order. Each tier has `from` (null = in effect now),
    `until` (null = until check-in and for a no-show) and `amount`.
  - Amounts: `PERCENT` = that percent of the stay. `NIGHTS` = the first N nights, with a fractional N charged pro rata.
    `FIXED` = the value in the rate's currency. Each amount is capped at the stay price.
  - Rules with value 0 and tiers already over aren't listed.
- Reviews: `PUBLISHED` only. The default order is newest first. `sort` accepts `createdAt` or `score` in either
  direction, and anything else gives 400. Ties go to the newest, then to the higher id. Items: `id, score, title, body, travellerType,
  createdAt, ownerReply, ownerRepliedAt`. The hotel's rating summary stays the stored `user_rating_avg`/`_count`.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Schema mappings and fixtures](./phase-01-schema-mappings-and-fixtures.md) | Completed |
| 2 | [Hotel overview API](./phase-02-hotel-overview-api.md) | Completed |
| 3 | [Room list with rates and cancellation](./phase-03-room-list-with-rates-and-cancellation.md) | Completed |
| 4 | [Reviews API](./phase-04-reviews-api.md) | Completed |
| 5 | [Docs and final verification](./phase-05-docs-and-final-verification.md) | Completed |

Run the phases in order, 1 → 5. Phase 2 needs phase 1's mappings. Phase 3 extends phase 2's `HotelController` and
`HotelService`. Phase 4 relies on the `/api/hotels/**` matcher that phase 2 adds to `SecurityConfig`. Phase 1 adds the
catalog helpers to `CatalogFixtures` and phase 4 adds the user and review helpers.

## Dependencies

None across plans.

## Acceptance Criteria

- [x] `GET /api/hotels/{slug}` returns the overview, gallery (by category), formatted address and coordinates,
      distance to centre, ratings, grouped facilities with free/paid, times and policies. Unlisted or unknown → 404.
- [x] `GET /api/hotels/{slug}/rooms` lists each sellable room type with photos, size, beds, max guests, features,
      `roomsLeft`, `fitsGuests` and every sellable rate (board, cancellation, price per night, total). Invalid stay → 400.
- [x] For one room, the cheapest fitting rate in the room list equals the search card's offer for the same stay
      (same rate plan and total), and the two show the same `freeUntil`.
- [x] Each rate's `penalties` gives exact tier times in the hotel's zone and amounts per room. The first paid tier
      starts at `freeUntil` when one is set, and a non-refundable rate's tier is already in effect.
- [x] `GET /api/hotels/{slug}/reviews` pages published reviews, newest first by default, sortable by score; hidden
      reviews are never returned; an unsupported sort → 400.
- [x] All three are public (no token needed) and documented without a bearer requirement.
- [x] Search, wishlist and destination tests pass unchanged; `./mvnw verify` passes.
- [x] README, CLAUDE.md and USER_STORIES (C-HOTEL-01/02/03/04/06/07 marked ✅, C-HOTEL-05 noted) are updated.

## Out of Scope

Writing reviews or policies (C-MYBK-05, O-HOTEL-*), moderation (A-REV-01), recomputing hotel ratings from reviews,
reviewer identity, score breakdown, nearby places (C-HOTEL-08), city-centre coordinates, a wishlist flag in the detail
response, applying search filters (board, cancellation…) to the room list, cart/quantity validation (C-CART-01),
currency conversion (C-SRCH-05), recently viewed hotels.

## Open Questions

None blocking. These go to later stories:
- C-MYBK-03 (refunds): is a `FIXED` penalty per room or per booking? This plan treats it as per room, like the
  prices.
- A product check: should exact `roomsLeft` counts be shown above a threshold, or only "Only N left"? The API returns
  the exact count and the client decides.

## Rollback

No commits. Before phase 1, back up `spring-base/` (without `target/`) to the session scratchpad as
`rollback/spring-base-before-c-hotel-detail.tgz`. Git is clean at `da7c4ae`, so `git status` lists every change. V8
is new. To undo it locally, drop `reviews` and `hotel_policies` and delete V8's `flyway_schema_history` row, or reset
with `docker compose down -v` (that wipes all local data).

## Implementation Notes (2026-10-28)

- Done as planned, with no fallbacks:
  - Hibernate validated the element collections (`Hotel.facilities`, `RoomType.beds`) and the `@ManyToMany`
    `RoomType.features`, so no link-table entities were needed.
  - The HQL join through the embeddable (`join h.facilities f join f.amenity am`) and the unrelated-entity left joins
    worked as written.
  - The search fragment split renders **byte-identical** SQL: `PRICED_OFFERS` was dumped from the compiled class
    before and after, then compared with `cmp`.
  - Hibernate 7 returns the native `bigint[]` column as `Long[]` (checked at runtime), so `nightPrices` keeps only
    that branch.
- One deviation: when nothing is sellable, `rooms` still runs the tax query. `taxesIncluded` depends on the country
  and the nights, not on the rooms, so it stays correct for an empty list. Room-type, rate and rule queries are still
  skipped, and `HotelServiceImplTest` covers this.
- Additions beyond the phase files:
  - `CatalogFixtures.roomTypeStatus`
  - a `roomPhoto` overload with category and order
  - `HotelBuilder.minCheckInAge` defaults to 18, the column default
  - `BedType`'s class doc updated with `Amenity`'s
  - `SecurityConfig`'s matcher comment says to keep authenticated hotel endpoints out of `/api/hotels`
- Tests: `./mvnw verify` passed **268** (219 before) from a clean report directory. The 49 new tests:

  | Test | Count |
  |---|---|
  | `HotelControllerTest` | 7 |
  | `HotelMapperTest` | 3 |
  | `HotelControllerRoomsTest` | 13 |
  | `HotelServiceImplTest` | 3 |
  | `CancellationScheduleTest` | 10 |
  | `ReviewControllerTest` | 5 |
  | `ReviewRepositoryTest` | 4 |
  | `ReviewServiceImplTest` | 4 |

  The only pre-existing test file touched is `CatalogFixtures`, with additions only.
- Seed: V8 and the reworked seed applied on the local database (port 5434). Re-running the whole seed file through
  `psql` changed no counts: 54 policies, 49 photos (18 room photos), no reviews, every demo hotel with a description,
  coordinates and room sizes. Mỹ Khê's room-only plans use the two-tier policy.
- Smoke run (port 8081, stay 2026-11-04 → 11-06):
  - overview of Mỹ Khê: formatted address `120 Võ Nguyên Giáp, Phước Mỹ, Sơn Trà, Đà Nẵng, Vietnam`, coordinates,
    gallery `exterior, lobby, restaurant, pool, room, room`, four facility groups, six policies
  - rooms, 2 adults: both room types and both rates each. Room-only shows `freeUntil` 2026-11-01T14:00+07:00, then 50 %
    until 11-03 14:00, then 100 %. Breakfast non-refundable shows one penalty in effect now.
  - the cheapest fitting rate (Deluxe Double BB, 2,700,000 VND) equals the search card's offer
  - rooms, 2 adults + 2 children: Family Suite first (fits), Deluxe Double last (`fitsGuests: false`)
  - errors and access:
    - Huế (inactive) → 404 on both endpoints
    - reviews → empty page; `sort=body` → 400
    - past check-in → 400
    - the API docs list all three operations with `security: []`
- No commits. The rollback copy is `rollback/spring-base-before-c-hotel-detail.tgz` in the session scratchpad. Git in
  `spring-base` was clean at `da7c4ae` before this work.

