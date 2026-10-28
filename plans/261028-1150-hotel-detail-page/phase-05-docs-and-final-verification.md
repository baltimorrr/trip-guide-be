---
phase: 5
title: Docs and final verification
status: completed
priority: P2
dependencies:
  - 1
  - 2
  - 3
  - 4
---

# Phase 5: Docs and final verification

## Overview

Bring README, CLAUDE.md and USER_STORIES in line with what phases 1–4 built. Then run the full suite and a local
smoke run against the demo seed, and record the outcome in `plan.md`. No commits.

## Context

- `.claude/rules/documentation-management.md`: read each doc before editing it, then verify dates, links and claims.
- `spring-base/README.md` sections: the intro, Endpoints, "Search and destinations", "Demo data", Wishlist, Tests,
  Project structure, Using this as a template, Known limitations.
- `spring-base/CLAUDE.md` "What this is" (the "Implemented so far" list), "Hotel catalog and search", Conventions.
- `spring-base/USER_STORIES.md` §4 and the "Last updated" header.
- `DATABASE-SCHEMA.md` needs **no edit**: `reviews` follows the doc exactly (user decision), and `hotel_policies`
  only applies the existing UPPERCASE-enum convention.

## Requirements

- Every claim in the docs matches the code and tests as built. Re-read the implemented classes rather than this plan
  where they differ, and note any difference in `plan.md`.
- Keep the docs' current voice: short declarative sentences, rules a maintainer needs, no changelog noise.

## Related Code Files

- Modify: `spring-base/README.md`, `spring-base/CLAUDE.md`, `spring-base/USER_STORIES.md`,
  `plans/261028-1150-hotel-detail-page/plan.md` (Implementation Notes, acceptance checkboxes)
- Create / Delete: none

## Implementation Steps

1. **README**
   - Intro: one sentence for the hotel detail page (C-HOTEL-01/02/03/04/06/07) linking a new
     `## Hotel detail page` section.
   - Endpoints table: three rows (`/api/hotels/{slug}`, `/rooms`, `/reviews`), public, with their 400/404s.
   - New `## Hotel detail page` section, in the style of "Search and destinations":
     - overview: address format, country name, gallery order and categories, facilities, policies
     - rooms: which room types and rates appear, `roomsLeft`, `fitsGuests`, prices per room, taxes
     - cancellation timeline: tier semantics, amounts, `from`/`until` nulls
     - reviews: published only, sort, why the local list is empty
     - curl examples
   - "Demo data": the new seed content (descriptions, coordinates, categorized photos, room sizes, policies, Mỹ Khê's
     two-tier policy, no reviews).
   - Tests table: `HotelControllerTest`, `HotelControllerRoomsTest`, `HotelMapperTest`, `HotelServiceImplTest`,
     `CancellationScheduleTest`, `ReviewControllerTest`, `ReviewRepositoryTest`, `ReviewServiceImplTest`.
   - Project structure: `hotel/` gains `controller/ services/ dto/ mapper/`; `review/`; migration V8.
   - "Using this as a template": add `review/` and `V8__create_hotel_policy_and_review_tables.sql`.
   - Known limitations, added or extended:
     - hotel ratings are stored and not recomputed from reviews
     - reviews are empty until C-MYBK-05
     - `FIXED` penalties are per room, and fractional `NIGHTS` are pro rata, until refunds (C-MYBK-03) decide
     - `fitsGuests` uses the even split
     - `roomsLeft` is exact
     - the public-bearer-token limitation now also covers `/api/hotels/**`
2. **CLAUDE.md**
   - "Implemented so far": the detail stories, `hotel/` API layers, `review/` and V8.
   - Hotel catalog rules:
     - `ROOM_TYPE_FITS`/`NIGHTS`/`FREE_ROOMS`/`RATE_OFFERS` are shared by search and the room list. Change them
       once, and the "matches search" test keeps the two equal.
     - `CancellationSchedule` builds the tiers, and `FreeCancellation.until` stays the only free-deadline definition
       in Java.
     - `SearchCriteria.today()`.
     - the link tables are now mapped as lazy collections (`Hotel.facilities`, `RoomType.beds/features`), read only
       through JPQL joins.
   - Reviews: `booking_id` has no FK until `bookings` exists. The migration that creates `bookings` adds it, and
     tests insert fake booking ids.
   - Security note: GET `/api/hotels/**` is public, so put owner/admin hotel endpoints under another prefix.
3. **USER_STORIES.md**
   - Mark C-HOTEL-01, 02, 03, 04, 06 and 07 "✅ Implemented" (07 as the read side only, with writing in C-MYBK-05).
   - Add a "(decided 28 Oct 2026)" note for each decision: unfit rooms marked, reviews' booking FK pending, no demo
     reviews.
   - Note under C-HOTEL-05 that the API is ready (the rooms endpoint takes the dates).
   - Bump "Last updated".
4. `./mvnw verify` with Docker running. Record the test count before and after, and confirm that no pre-existing
   test was edited (`git diff --stat src/test` shows only `CatalogFixtures` among the existing files).
5. Local smoke run (`./mvnw spring-boot:run`, port 8081 from `.env`, demo seed):
   - `/api/hotels/my-khe-beach-resort-demo`: description, formatted address, coordinates, gallery categories,
     grouped facilities, policies
   - `/rooms` for a stay a week out with 2 adults: both room types and both rates. The Family Suite fits, and the
     Mỹ Khê room-only rate shows the two tiers.
   - the same with `adults=4`: Deluxe Double gets `fitsGuests: false`
   - `/api/hotels/hue-imperial-demo` → 404. `/reviews` → an empty page. `sort=body` → 400.
6. Write the Implementation Notes in `plan.md`: what was done, any fallbacks taken, test counts, smoke results and
   the rollback copy's location. Tick the acceptance criteria that hold, and leave the others unticked with the
   reason. Set phase statuses with `ck plan check`.

## Success Criteria

- [x] README, CLAUDE.md and USER_STORIES describe the built behaviour, and each claim was checked against code or
      tests.
- [x] `./mvnw verify` passes, and pre-existing tests are unchanged.
- [x] The smoke run results are recorded in `plan.md`.
- [x] No commits or staging.

## Risk Assessment

- **Docs drifting from the code.** Write them from the implemented classes, not from this plan.
- **README length.** The new section should be about the size of the Wishlist section plus the cancellation rules.
  Link to code (`CancellationSchedule`) rather than restating every edge case.
