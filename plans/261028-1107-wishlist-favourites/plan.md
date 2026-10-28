---
title: Wishlist / favourites (C-SRCH-08)
description: >-
  Signed-in customers save hotels to one wishlist (idempotent PUT), remove them
  (idempotent DELETE) and list them as hotel cards, newest first, each marked
  available or not. New V7 `wishlists` table with the schema doc's composite
  key. Search endpoints stay unchanged: clients draw the hearts from GET
  /api/wishlist.
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - wishlist
blockedBy: []
blocks: []
created: 2026-10-28T00:00:00.000Z
---

# Wishlist / favourites (C-SRCH-08)

## Overview

Sources: `spring-base/USER_STORIES.md` §3 C-SRCH-08 ("save hotels to a wishlist so that I can compare and book
later"; saving is idempotent; hearts on result cards show saved hotels), §1 account deletion and §10 privacy (wishlist
deleted with the account). `spring-base/DATABASE-SCHEMA.md` `wishlists(user_id, hotel_id, created_at)`, PK
`(user_id, hotel_id)`, cascade on both FKs.

All other plans in `plans/` are completed, so nothing blocks this plan and it blocks nothing. Git in `spring-base` is
clean at `c619780`.

## User Decisions (2026-10-28, do not reverse silently)

| Decision | Choice |
|----------|--------|
| Scope | **Hold**: save, remove, list of hotel cards. No prices in the list (prices need dates; the client opens the hotel with its last search) |
| Hearts on result cards | **From a separate wishlist endpoint that returns full cards** (`GET /api/wishlist`), not a `saved` flag on search results and not an ids-only endpoint. Search is untouched |
| Hotels that stop being listed | **Shown as unavailable**: they stay in the list with `available: false`. They're never hidden or deleted |
| Git | No commits or staging by Claude (standing preference) |

## Planner Defaults (changeable)

- `GET /api/wishlist?page=&size=`, `PUT /api/wishlist/{hotelId}`, `DELETE /api/wishlist/{hotelId}`. Each needs a
  signed-in user (any role); without a token they answer 401. The user always comes from the token's `sub`.
- PUT answers 204 for a new save and for a repeat, and a repeat keeps the original `savedAt`. It answers 404 when the
  hotel doesn't exist or isn't listed (`ACTIVE`, not deleted).
- DELETE answers 204 whether or not the hotel was saved, including for a hotel that is no longer listed.
- The list is newest saved first, with ties going to the higher hotel id first. `sort` is ignored. Page size is 20 by
  default and capped at 100 globally.
- Each item has the hotel card fields (as in search, with `coverPhotoUrl` instead of the photo carousel), plus
  `available` and `savedAt`.
- One flat list per user, as in the schema doc. Guests have no wishlist on the server.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Wishlist schema and API](./phase-01-wishlist-schema-and-api.md) | Completed |
| 2 | [Docs and final verification](./phase-02-docs-and-final-verification.md) | Completed |

Phase 2 depends on phase 1.

## Acceptance Criteria

- [x] Saving a hotel twice gives 204 both times and one entry. Concurrent saves never give 409 (`on conflict do nothing`).
- [x] `GET /api/wishlist` returns the user's hotels as cards: paged, newest first, with `savedAt` and `available`.
- [x] An inactive or soft-deleted hotel stays in the list with `available: false`, and it shows `true` again once it
      is listed again.
- [x] DELETE is idempotent. PUT on an unknown or unlisted hotel gives 404. No token gives 401. One user's calls never
      read or change another user's wishlist.
- [x] Search endpoints and their tests are unchanged.
- [x] `./mvnw verify` passes. README, CLAUDE.md and USER_STORIES (C-SRCH-08 ✅) are updated.

## Out of Scope

Prices or availability for dates in the wishlist, several named lists, a guest wishlist (or merging one on sign-in),
hearts on popular destinations or the hotel detail page (C-HOTEL-01 not built), wishlist in a data export or account
deletion endpoint (cascade covers deletion when that exists), recommendations (C-HOME-04), save counts per hotel.

## Open Questions

None blocking. A later product check: should unavailable hotels move to the end of the list instead of staying in
saved order?

## Rollback

No commits. Before phase 1, back up `spring-base/` (without `target/`) to the session scratchpad as
`rollback/spring-base-before-c-srch-08.tgz`. Git is clean at `c619780`, so `git status` lists every change. V7 is new.
To undo it locally, either drop `wishlists` and delete its `flyway_schema_history` row, or reset the local DB with
`docker compose down -v`, which also wipes all local data.

## Implementation Notes (2026-10-28)

- Done as planned, with no fallbacks. The HQL `insert … on conflict do nothing` works with the `@IdClass` record key,
  and `case when … then true else false end` fills the view's primitive `boolean`. Flyway applied V7, and
  `ddl-auto: validate` accepted `WishlistEntry`.
- `./mvnw verify` passed: 219 tests (208 before). The only new ones are `WishlistControllerTest` (7) and
  `WishlistServiceImplTest` (4). Search tests ran unchanged.
- Concurrent saves are covered by PostgreSQL's `on conflict do nothing` semantics, not by a parallel test. The tests
  save twice in a row.
- Additions beyond the phase files: a page past the end still reports `totalElements`, and the README's "Using this as
  a template" list now also names `V6__create_amenity_and_bed_tables.sql`, which it had left out.
- Local smoke run (seed, port 8081):
  - no token → 401
  - saving twice → 204, 204, and one card with every field plus `available`/`savedAt`
  - the inactive Huế hotel → 404 `Hotel 9 not found`
  - setting a saved hotel `INACTIVE` → `available:false`, and restoring it → `true`
  - deleting twice → 204, 204, and an empty list
  - The smoke account (`smoke-*@example.com`) stays in the local DB.
- No commits. The rollback copy is `rollback/spring-base-before-c-srch-08.tgz` in the session scratchpad. Git in
  `spring-base` was clean at `c619780` before this work.
