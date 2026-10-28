---
phase: 2
title: Docs and final verification
status: completed
priority: P2
dependencies:
  - 1
---

# Phase 2: Docs and final verification

## Overview

Document the wishlist API and the one new persistence pattern, mark C-SRCH-08 implemented, then run the full suite
and a local smoke test.

## Requirements

- Docs describe behaviour that phase 1 actually shipped, including any fallback taken (native insert, `Boolean`
  view field).
- No change to `DATABASE-SCHEMA.md`. `wishlists` matches the doc apart from the bigint ids and the `created_at`
  without a default, which the 2026-10-21 decision already covers.

## Related Code Files

- Modify: `spring-base/README.md`, `spring-base/CLAUDE.md`, `spring-base/USER_STORIES.md`

## Implementation Steps

1. **README.md** (read it first):
   - Intro: one sentence on the wishlist (C-SRCH-08) and a link to the new section.
   - Endpoints table:
     - `GET /api/wishlist`: authenticated, paged hotel cards, newest first, `available`, `savedAt`.
     - `PUT /api/wishlist/{hotelId}`: 204 whether new or repeated, 404 for an unknown or unlisted hotel.
     - `DELETE /api/wishlist/{hotelId}`: always 204.
   - New `## Wishlist` section after "Search and destinations":
     - one list per account
     - idempotent save that keeps the first `savedAt`
     - remove always succeeds
     - newest first, `sort` ignored
     - `available: false` once a hotel is inactive or deleted (it can't be opened or booked; remove it or wait for it
       to come back)
     - no prices: open the hotel with the last search
     - **hearts:** search results don't say what's saved. The client reads the hotel `id`s from `GET /api/wishlist`
       (page through with `size=100`) and draws the hearts from them.
     - curl examples for PUT, GET and DELETE with `$TOKEN`
   - Tests table: `WishlistControllerTest`, `WishlistServiceImplTest`.
   - Project structure: `wishlist/` with its layers. Migrations line: `V7 wishlists`.
   - "Using this as a template" step 3: `wishlist/` and `V7__create_wishlist_table.sql` go with the hotel features.
2. **CLAUDE.md**:
   - "Implemented so far": add the wishlist C-SRCH-08 (`wishlist/`, migration V7).
   - Next to the `DestinationSyncRepository` bullet: `WishlistRepository` is also a plain interface + `@Repository`
     impl.
   - Persistence or Conventions: `WishlistEntry` is the only entity without `BaseEntity` (composite key
     `(user_id, hotel_id)`, only `created_at`, set by the service). Saving is one HQL `insert … on conflict do
     nothing`, so a double save never gives 409. Removing is a JPQL bulk delete, so a concurrent double delete never
     fails. Adjust this if phase 1 took the native fallback.
3. **USER_STORIES.md**: C-SRCH-08 → `✅ Implemented`. Sub-bullets, decided 28 Oct 2026:
   - hearts come from the wishlist endpoint, and search results are unchanged
   - a hotel that stops being listed stays in the wishlist as unavailable
   - no prices in the list
4. Re-read each edited doc. Check that endpoint paths, status codes, field names, migration number and test class
   names match the code.
5. **Verify:**
   - `./mvnw verify` with Docker running. Record the test count against the phase 1 baseline.
   - Local smoke run on port 8081 (this machine's `.env`; 8080 and 5432 are taken):
     1. register, verify (the link is in the log) and log in
     2. PUT a seed hotel twice, then GET → one entry
     3. PUT the seed's inactive Huế hotel → 404
     4. `update hotels set status = 'INACTIVE'` on the saved hotel → GET shows `available: false`, then restore it
     5. DELETE twice → 204, 204
6. Update `plan.md`: tick the acceptance criteria and add "Implementation Notes" (test counts, any fallback taken,
   anything learned). Mark phases with `ck plan check`.

## Success Criteria

- [x] `./mvnw verify` passes, and the test count rises by the new tests only.
- [x] Smoke run behaves as documented.
- [x] README, CLAUDE.md and USER_STORIES match the shipped behaviour. No stale claim that search marks saved hotels.

## Risk Assessment

- Docs could drift from code if phase 1 took a fallback. Step 4 re-checks claims against the code.
- The smoke run needs Docker and a free port 8081. If either is missing, note it and rely on `./mvnw verify`.
