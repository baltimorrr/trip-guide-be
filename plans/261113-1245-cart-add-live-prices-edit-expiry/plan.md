---
title: 'Cart: add rooms, live prices, edit, expiry (C-CART-01/02/03/04)'
description: >-
  A server cart for signed-in customers: add rooms from any hotel and dates,
  grouped by hotel and date range; priced live on every read by the hotel page's
  own availability query (old vs new price, unavailable lines marked); change
  quantity or dates, remove; merge the browser's guest cart on sign-in; an
  hourly job deletes lines whose check-in passed. New V9 carts/cart_items.
  Checkout stays with C-BOOK-01.
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - cart
blockedBy: []
blocks: []
created: 2026-11-13T00:00:00.000Z
---

# Cart: add rooms, live prices, edit, expiry (C-CART-01/02/03/04)

## Overview

Sources: `spring-base/USER_STORIES.md` §5 C-CART-01 (updated 13 Nov 2026: several hotels and dates, grouped by hotel
and date range, one group = one checkout), C-CART-02, C-CART-03, C-CART-04. `spring-base/DATABASE-SCHEMA.md`
`carts`, `cart_items` (also edited 13 Nov 2026: dates live on the items). Both files have the user's uncommitted
edits on top of `b29ba82`; build on them, never revert them.

Every other plan in `plans/` is completed, so nothing blocks this plan and it blocks nothing.

| Endpoint (signed-in, any role) | Stories |
|---|---|
| `GET /api/cart` | 02 live prices and availability, grouped (01) |
| `POST /api/cart/items` | 01 add |
| `PUT /api/cart/items/{itemId}` | 03 change quantity or dates |
| `DELETE /api/cart/items/{itemId}` | 03 remove |
| `POST /api/cart/merge` | 01 guest cart merged on sign-in |
| `CartExpiryJob` (scheduled) | 04 expiry |

## User Decisions (2026-11-13, do not reverse silently)

| Decision | Choice |
|----------|--------|
| Guest carts | **Browser-only.** The server keeps carts for signed-in users only: `carts.user_id not null unique`, **no `guest_token`** (the schema doc is edited to match). Guests price their browser cart with the public `GET /api/hotels/{slug}/rooms`; after sign-in the client sends it to `POST /api/cart/merge` |
| Same line in both carts on merge | **Keep the larger quantity** (`max`), so the same pick on two devices doesn't double the rooms and a retried merge changes nothing. Adding a line that is already in the cart (`POST /items`) **adds up** the quantities |
| Scope | **Hold**: the four stories as written. Checkout and its "one hotel, one date range" rejection come with C-BOOK-01 |
| Git | No commits or staging by Claude (standing preference) |

## Planner Defaults (changeable)

- **A line** = rate plan (room type + board + cancellation) + `checkIn`/`checkOut` + guests per room (`adults`,
  `childAges`, stored sorted) + `quantity` (rooms). One line per such key per cart (unique index). A **group** = the
  lines of one hotel with the same `checkIn`/`checkOut`: one future booking.
- **Pricing reuses `HotelRepositoryImpl.findRoomRates` unchanged**: one call per distinct (hotel, dates, guests per
  room) of the cart. So the cart, the hotel page and search agree on what can be sold and at what price, and
  `night_price()` is never re-implemented. Prices are per room in the hotel's currency, before taxes added on top.
- **Line status on every read** (C-CART-02):
  - `CHECK_IN_PASSED`: check-in is before `SearchCriteria.today()`. No SQL is run for these lines.
  - `UNAVAILABLE`: the rate plan isn't in the room list for that stay, or its room type no longer fits the guests.
  - `NOT_ENOUGH_ROOMS`: the group's lines of that room type need more rooms than `roomsLeft`.
  - `AVAILABLE`: otherwise.
- A group's `canCheckout` is true when every line is `AVAILABLE`. Its `total` sums the `AVAILABLE` lines. Groups are
  ordered by check-in, hotel name, then hotel id; lines within a group by when they were added.
- **Price-changed notice**: `priceAtAdd` (per room, whole stay) is stored when a line is added. Every read compares it
  with the current per-room price (`priceChanged`, old and new both returned). It is reset to the current price when
  the user picks a stay at today's price: adding the line (again) or changing its dates. A quantity-only edit keeps
  it. New lines from a merge start at the merge's current price (the server never trusts a client-sent price). A
  merge that raises an existing line's quantity keeps that line's price.
- **Writes check what reads report**: add and edit answer
  - 400 for stay rules (`SearchCriteria.of`: past, too far ahead, too long, limits) and for guests the room type
    can't hold
  - 404 for an unknown rate plan, and for an item that doesn't exist or belongs to another user
  - 409 for "not sellable for these dates", "only N rooms left" (counting the group's other lines of that room type)
    and "cart full"
  On failure nothing changes. Add and edit answer 200 with the whole priced cart; merge answers 200 with
  `{cart, skipped}`. DELETE always answers 204.
- Cart size: at most `app.cart.max-items` lines (default 20), which bounds the pricing queries per read. Quantity and
  guests use the search limits (`app.search.*`).
- Merge: `{items: [...]}` of add requests, at most `max-items` (else 400). Each item goes through add's checks, with
  `max` for an existing line. An item that fails is skipped and reported as `{index, reason}` instead of failing the
  call.
- Concurrency: every cart write locks the user's `carts` row (`PESSIMISTIC_WRITE`, like `findByTokenHashForUpdate`),
  so the rooms check and the line merge see a stable cart. The first-ever add creates the row; two first adds at the
  same moment give one 409 (unique `user_id`), the project's usual race rule.
- Expiry (C-CART-04): `CartExpiryJob` runs a JPQL bulk delete of lines with `check_in < SearchCriteria.today()` on
  `app.cart.expiry.cron` (default hourly; `"-"` in tests). The `carts` row stays. Reads already mark such lines
  `CHECK_IN_PASSED`, so the job's timing never shows a stale line as bookable.
- Rate limits: `GET /api/cart`, `POST /api/cart/items`, `PUT /api/cart/items/*` and `POST /api/cart/merge` run the
  priced availability SQL, so they also spend from the `search` limit.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Cart schema and mappings](./phase-01-cart-schema-and-mappings.md) | Completed |
| 2 | [Priced cart and add to cart](./phase-02-priced-cart-and-add-to-cart.md) | Completed |
| 3 | [Edit merge and expiry](./phase-03-edit-merge-and-expiry.md) | Completed |
| 4 | [Docs and final verification](./phase-04-docs-and-final-verification.md) | Completed |

Run the phases in order, 1 → 4. Phase 2 needs phase 1's entities and repositories. Phase 3 reuses phase 2's line
checks and the priced read.

## Dependencies

None across plans.

## Acceptance Criteria

- [x] A signed-in user adds rooms from two hotels, and for two date ranges at one hotel. `GET /api/cart` returns three
      groups (hotel + dates), each line with its rate, board, cancellation, quantity, guests, status and prices.
- [x] Adding the same line again adds up the quantities, stays one line and resets `priceAtAdd`.
- [x] Add/edit reject:
      - 400: past or invalid stay, guests the room can't hold
      - 404: unknown rate plan, or another user's item
      - 409: unsellable stay, more rooms than are left in the group, cart full
      On failure the cart is unchanged.
- [x] After a price change, the line shows `priceAtAdd`, the current price and `priceChanged: true`. A closed night,
      an inactive plan or an unlisted hotel gives `UNAVAILABLE`, fewer rooms gives `NOT_ENOUGH_ROOMS`, and either
      sets the group's `canCheckout: false`.
- [x] A cart line's price equals `GET /api/hotels/{slug}/rooms`'s `totalPrice` for the same rate and stay.
- [x] Quantity edits keep `priceAtAdd`. Date edits move the line to its new group, reset `priceAtAdd`, and merge into
      an equal line. DELETE is idempotent.
- [x] Merge keeps the larger quantity, adds new lines, skips failing items with `{index, reason}`, and a second
      identical merge changes nothing.
- [x] `CartExpiryJob`'s delete removes only lines whose check-in is before `SearchCriteria.today()`; until it runs
      they read `CHECK_IN_PASSED`.
- [x] No token → 401. One user's calls never read or change another user's cart.
- [x] Hotel page, search and wishlist tests pass unchanged; `./mvnw verify` passes.
- [x] README, CLAUDE.md, USER_STORIES (C-CART-02/03/04 ✅, C-CART-01 🟡 until checkout) and DATABASE-SCHEMA are
      updated.

## Out of Scope

Checkout, holds and the mixed-group rejection (C-BOOK-01/04), price breakdown with tax amounts (C-BOOK-02),
discounts (C-BOOK-03), currency conversion (C-SRCH-05), server-side guest carts, a public "price this cart" endpoint,
editing guests on a line (remove + add), clearing the whole cart, abandoned-cart emails, deleting empty `carts` rows.

## Open Questions

None blocking. For later stories:
- C-BOOK-01: does checkout take a group as `{hotelId, checkIn, checkOut}` or as a list of item ids? This plan returns
  both in the group, so either works.
- Product check: should a merged guest line keep the browser's "price changed" notice? This plan resets it, because
  a client-sent price can't be trusted.

## Rollback

No commits. Before phase 1, back up `spring-base/` (without `target/`) to the session scratchpad as
`rollback/spring-base-before-c-cart.tgz`. Git is at `b29ba82` with the user's uncommitted `DATABASE-SCHEMA.md` and
`USER_STORIES.md` edits, so take `git diff` of those two files into the same folder first. V9 is new. To undo it
locally, drop `cart_items` and `carts` and delete V9's `flyway_schema_history` row, or reset with
`docker compose down -v` (that wipes all local data).

## Implementation Notes (2026-11-13)

- Done as planned, with no fallbacks:
  - `CartItem.childAges` maps `smallint[]` as a plain `Short[]`, and `ddl-auto: validate` accepted it.
  - `select new` reads it through a second `CartLineView` constructor.
  - The pricing reuses `findRoomRates` unchanged. `git diff` of `HotelRepositoryImpl` shows only the new
    `findListedIds` JPQL, and no SQL text block changed.
- Deviations from the phase files:
  - The cron key is `app.cart.expiry-cron` (flat, in `CartProperties`), not `app.cart.expiry.cron`.
  - `CartItemRepositoryTest` is an `@IntegrationTest`, like `ReviewRepositoryTest`, so it shares the one context
    instead of starting a `@DataJpaTest` slice.
  - The writes load the locked cart's lines with `findInCart` (join fetch of rate plan and room type), so neither
    `findByCartIdOrderByIdAsc` nor `CartRepository.findByUserId` was added.
  - The merge's max rule and the quantity-only edit are covered by `CartControllerMergeTest` and
    `CartControllerEditTest`, not by extra Mockito cases.
- Additions beyond the phase files:
  - `RoomGuests`, a value object that sorts the ages
  - `CatalogFixtures.userId(email)`
  - the `CartClient` test helper in `cart/controller`
  - `RateLimitPolicyTest`, since no policy test existed
  - the `search` comment in `application.yml`
- Tests: `./mvnw verify` passed **324** (282 before) from a clean report directory. The 42 new tests:

  | Test | Count |
  |---|---|
  | `CartControllerTest` | 11 |
  | `CartControllerPricesTest` | 6 |
  | `CartControllerEditTest` | 7 |
  | `CartControllerMergeTest` | 6 |
  | `CartItemRepositoryTest` | 4 |
  | `CartServiceImplTest` | 4 |
  | `CartExpiryTest` | 1 |
  | `RateLimitPolicyTest` | 3 |

  Pre-existing test files touched: `CatalogFixtures` (additions only) and `application-test.yml` (the cart cron set
  to `"-"`).
- Smoke run (local profile, port 8081, demo seed, `APP_CART_EXPIRY_CRON="*/10 * * * * *"`):
  - V9 applied
  - no token → 401
  - adds:
    - Sông Hàn plan 1 for 10–12 Dec and 20–21 Dec, plus Hàn Market plan 5 for 10–12 Dec → three groups ordered by
      check-in, then name
    - Sông Hàn's weekend rule shows in its price (2,640,000 for two nights)
    - plan 2 ×5 next to plan 1 ×1 on one room type → 409 "Only 5 rooms of this type are left for these dates"
  - edits:
    - quantity 3, then a 1,500,000 override on 10 Dec → `priceChanged: true`, with `priceAtAdd` still 2,640,000
    - moving that line onto the 20–21 Dec line → one line with quantity 3
  - closing 11 Dec for plan 5 → `UNAVAILABLE`, with `canCheckout: false` and `total: null`
  - merge: the duplicate with a smaller quantity kept 3, Mỹ Khê was added, and the past item and the unknown plan
    were skipped with index and reason
  - DELETE twice → 204, 204
  - a past line written with SQL read `CHECK_IN_PASSED`, and the next tick logged "Removed 1 cart items…" and
    deleted it
  - The two demo price rows were deleted afterwards. The smoke account (`smoke-*@example.com`) and its cart stay in
    the local DB.
- Not done: a row in USER_STORIES' "Changes in this version" table. That table is the user's changelog; ask first.
- No commits. The rollback copy is `rollback/spring-base-before-c-cart.tgz` in the session scratchpad, next to
  `user-doc-edits-before-c-cart.diff`. Before this work, git in `spring-base` was at `b29ba82`, with only the user's
  `DATABASE-SCHEMA.md` and `USER_STORIES.md` edits.
