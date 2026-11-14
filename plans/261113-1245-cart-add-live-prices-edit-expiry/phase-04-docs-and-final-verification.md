---
phase: 4
title: Docs and final verification
status: completed
priority: P2
dependencies:
  - 3
---

# Phase 4: Docs and final verification

## Overview

Run the whole suite and a local smoke test. Then update README, CLAUDE.md, USER_STORIES and DATABASE-SCHEMA so they
describe the cart as built, and record the implementation notes in `plan.md`.

## Context

- `.claude/rules/documentation-management.md`: read each doc before editing; check dates, links and claims afterwards
- `USER_STORIES.md` and `DATABASE-SCHEMA.md` carry the user's uncommitted 13 Nov edits. Edit on top of them and
  keep their wording.
- The README sections "Endpoints", "Rate limits", "Configuration", "Using this as a template", "Known limitations";
  the CLAUDE.md "What this is" paragraph and Persistence/Architecture bullets
- `~/.claude/projects/-home-jazzy-Personal-java/memory/dev-machine-ports.md`: the local app runs on 8081 and
  Postgres on 5434

## Requirements

- Functional: the docs match the code, including the user decisions and the deviations from the schema doc.
- Non-functional: no changelog noise; README stays the endpoint/behaviour reference, CLAUDE.md the maintainer
  contract.

## Related Code Files

- Modify: `README.md`
  - five rows in the Endpoints table
  - a new "Cart" section after "Hotel detail page": lines and groups, statuses, the notice, the merge rules, errors,
    expiry
  - Rate limits: the `search` row gains the cart paths
  - Configuration: `APP_CART_EXPIRY_CRON`, `app.cart.max-items`
  - "Using this as a template": V9
  - Known limitations: see below
  - the intro's feature list
- Modify: `CLAUDE.md`
  - "Implemented so far" gains C-CART-01/02/03/04 (`cart/`, migration V9)
  - **Decided (2026-11-13), cart**: browser-only guest carts (no `guest_token`, merge on sign-in); merge keeps the
    larger quantity, add sums; checkout of one group comes with C-BOOK-01
  - Persistence/catalog bullets:
    - cart prices come from `findRoomRates` once per (hotel, dates, guests per room), never from new SQL
    - every cart write locks the `carts` row
    - one line per key (`cart_items_line_key`, ages stored sorted)
    - `childAges` is the first array-mapped entity field
  - Tests: the `CatalogFixtures.cartItem` / `defaultRatePlan` helpers
- Modify: `USER_STORIES.md`
  - C-CART-02, C-CART-03 and C-CART-04 ✅
  - C-CART-01 🟡, with "left: the checkout's rejection of mixed groups (C-BOOK-01)"
  - one decision row in "Changes in this version" for the guest-cart and merge decisions, if the user agrees (see
    step 5)
- Modify: `DATABASE-SCHEMA.md`
  - `carts`: `guest_token` and its index and CHECK removed, `user_id` not null + unique
  - `cart_items`: `cart_items_line_key` unique and `cart_items_check_in_idx` replace `cart_items_cart_id_idx`, plus
    the `children`/ages CHECKs
  - the header comment line "Edited by hand…" gains "carts (no guest_token)"
- Modify: `plans/261113-1245-cart-add-live-prices-edit-expiry/plan.md`: status, ticked criteria, Implementation Notes
  (run `ck plan check <phase-id>` for the phase rows; don't hand-edit the table)

Known limitations to add:
- prices are per room in the hotel's currency with a tax yes/no flag, with no conversion or tax amounts
- the notice resets when a stay is re-picked and on merge
- a read runs one priced availability query per distinct stay (at most `max-items`)
- the cart shares the `search` rate limit
- two simultaneous first adds give one 409
- empty cart rows are kept

## Implementation Steps

1. Run `./mvnw verify` from a clean report directory. Record the test count before and after (268 before the hotel
   page plan; read the current count from the first run) and list the new test classes.
2. Smoke test on the local profile (8081 / 5434):
   - V9 applies
   - sign in as a fresh `smoke-*@example.com` account
   - add two demo hotels' rates for two stays
   - GET → groups
   - edit quantity and dates, delete, merge (including a duplicate and a past item)
   - set a night price with `psql` → `priceChanged`
   - close a night → `UNAVAILABLE`
   - insert a past line and tick the job (temporary `APP_CART_EXPIRY_CRON="*/10 * * * * *"`) → line removed
   - no token → 401
3. Read each doc, then edit it as listed. Re-read the edited parts and check every claim against the code: paths,
   defaults, status codes, limits.
4. Fill in `plan.md` "Implementation Notes", as in the earlier plans:
   - fallbacks used, especially the array mapping
   - deviations from this plan
   - test counts
   - the smoke results
   - where the rollback copy is
5. Ask the user before adding a row to USER_STORIES' "Changes in this version" table. It's their changelog, and they
   wrote today's row themselves.

## Success Criteria

- [x] `./mvnw verify` passes. The only pre-existing test files touched are `CatalogFixtures` (additions) and none
      else.
- [x] Every smoke step behaves as `plan.md` says.
- [x] README, CLAUDE.md, USER_STORIES and DATABASE-SCHEMA match the code. The user's 13 Nov wording is intact.
- [x] `plan.md` status is completed, with implementation notes. No commits.

## Risk Assessment

- **Overwriting the user's uncommitted doc edits**: use targeted `Edit` calls only, never a full `Write` of those
  files. Compare `git diff` before and after against the backup taken in the Rollback step.
- **Doc drift**: CLAUDE.md is long and dense. Add only rules a maintainer would otherwise break: the pricing reuse,
  the lock, the line key and the decisions. The rest goes in README.
