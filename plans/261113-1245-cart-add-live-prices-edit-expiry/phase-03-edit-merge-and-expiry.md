---
phase: 3
title: Edit merge and expiry
status: completed
priority: P1
dependencies:
  - 2
---

# Phase 3: Edit merge and expiry

## Overview

This phase covers C-CART-03 (change a line's quantity or dates, remove it), the sign-in merge from C-CART-01
(`POST /api/cart/merge`) and C-CART-04 (`CartExpiryJob`). Every write reuses phase 2's `checkLine` and returns the
priced cart, except DELETE.

## Context

- `plan.md` "User Decisions" (merge = keep the larger quantity) and "Planner Defaults" (notice reset rules, errors)
- Phase 2: `CartServiceImpl.checkLine`, `add`, `cart`, `CartItemRequest`
- `destination/sync/DestinationSyncJob`: the job shape (`@Scheduled(cron = "${...}")`, catch and log, never stop
  the scheduler); `common/config/SchedulingConfig`
- `wishlist/controller/WishlistController.remove`: idempotent 204 DELETE

## Requirements

- Functional:
  - `PUT /api/cart/items/{itemId}` with `CartItemUpdateRequest { @NotNull checkIn, @NotNull checkOut, @NotNull
    @Min(1) quantity }` → 200 `CartResponse`.
    - Guests and rate plan stay the same; changing them is remove + add.
    - 404 for an unknown item or another user's item. 400/409 as for add.
  - `DELETE /api/cart/items/{itemId}` → always 204, and only the caller's own line is ever deleted.
  - `POST /api/cart/merge` with `CartMergeRequest { @NotNull List<@NotNull @Valid CartItemRequest> items }` →
    200 `CartMergeResponse { cart: CartResponse, skipped: [{ index, reason }] }`.
    - More than `maxItems` items → 400.
    - An empty list → 200 with the current cart.
  - The expiry job deletes every line with `check_in < SearchCriteria.today()`.
- Non-functional:
  - writes hold the cart row lock for their whole transaction
  - merge runs at most one `checkLine` per item
  - the job is idempotent and safe to run on several instances at once

## Architecture

`update(userId, itemId, request)`, `@Transactional`:

1. Lock the cart with `findByUserIdForUpdate(userId)`; if it is missing → 404 `ResourceNotFoundException("Cart item",
   itemId)`. Load the items and find `item` by id, or throw the same 404.
2. Look for a `target` line: another line with the item's rate plan, guests and the **new** dates.
3. Run `checkLine` for the new stay:
   - with a `target`: quantity = `target.q + request.q`, `excludeIds = {item.id, target.id}`
   - otherwise: quantity = `request.q`, `excludeIds = {item.id}`
4. Apply it:
   - **Target exists** (the dates now equal another line): `target.changeQuantity(sum)`,
     `target.rebasePrice(row)`, delete `item`. The two lines become one; the response no longer has `itemId`.
   - **Dates changed**: `item.changeStay(checkIn, checkOut, row.stayPriceMinor, hotelCurrency)`. The new stay
     resets `priceAtAdd`.
   - **Quantity only**: `item.changeQuantity(q)`. `priceAtAdd` is kept, so the notice survives.
5. `return cart(userId)`.

`remove(userId, itemId)` is `@Transactional` and calls `cartItems.deleteByIdAndCartUserId(itemId, userId)`. It takes
no lock: the worst a concurrent add sees is one line too many in its rooms check, which is conservative.

`merge(userId, request)` is `@Transactional`:

1. If `items.size() > maxItems` → 400.
2. Lock or create the cart, and load its items once.
3. For each `items[i]` in order, do the add steps against the in-memory list. Catch `BusinessException` and
   `ResourceNotFoundException` per item, and record `{i, ex.getMessage()}` as skipped:
   - an equal line exists → `quantity = max(existing.q, item.q)`. When that's no more than `existing.q`, don't
     touch the line at all: no check, no rebase. A retried merge is then a no-op even if prices moved. When it's
     more, run `checkLine` (excluding `existing`) and call only `changeQuantity`. The account line keeps its
     `priceAtAdd`, so its notice survives.
   - otherwise, a cart already at `maxItems` lines → skip with "cart full", else `checkLine` and create the line
4. `return new CartMergeResponse(cart(userId), skipped)`.

`checkLine` must see lines merged earlier in the same call (the rooms check), so pass the in-memory list rather than
re-querying.

`CartExpiryJob` (`cart/expiry/`) is package-private, like `DestinationSyncJob`:

```java
@Scheduled(cron = "${app.cart.expiry.cron}")
void removeExpired() {
	try {
		int removed = cartService.removeExpiredItems();
		if (removed > 0) log.info("Removed {} cart items whose check-in has passed", removed);
	}
	catch (RuntimeException ex) {
		log.error("Cart expiry failed", ex);
	}
}
```

`CartService.removeExpiredItems()` is `@Transactional` and returns
`cartItems.deleteCheckedInBefore(SearchCriteria.today())`. The cutoff is the UTC−12 date, so a line is only removed
once its check-in date has passed everywhere, which is the same rule add and read use. The job doesn't run on
startup, because reads already mark such lines `CHECK_IN_PASSED`.

## Related Code Files

- Create: `cart/dto/{CartItemUpdateRequest, CartMergeRequest, CartMergeResponse, SkippedCartItem}.java`
- Create: `cart/expiry/CartExpiryJob.java`
- Modify: `cart/controller/CartController.java` (PUT, DELETE, merge)
- Modify: `cart/services/CartService.java` and `cart/services/impl/CartServiceImpl.java` (`update`, `remove`,
  `merge`, `removeExpiredItems`)
- Create tests: `cart/controller/CartControllerEditTest.java`, `cart/controller/CartControllerMergeTest.java`,
  `cart/services/impl/CartExpiryTest.java` (`@IntegrationTest`, calls `CartService.removeExpiredItems()` directly)
- Modify test: `cart/services/impl/CartServiceImplTest.java` (merge max rule; quantity-only edit keeps the price)

## Implementation Steps

1. `update`, `remove`, `merge` and `removeExpiredItems`, with short comments on the non-obvious rules: max on
   merge, and the rebase cases.
2. Controller methods. Javadoc gives each one's answers and error codes; DELETE says "204 whether or not the line
   existed".
3. `CartExpiryJob`.
4. Tests:
   - `CartControllerEditTest`:
     - a quantity change keeps `priceAtAdd` after a price override
     - a date change moves the line to a new group and resets `priceAtAdd`
     - a date change onto an equal line → one line with the summed quantity
     - an edit onto unsellable dates → 409 and the line unchanged
     - an edit over `roomsLeft` → 409
     - another user's item: PUT → 404, DELETE → 204 and the owner's line still there
     - DELETE twice → 204, 204
     - no token → 401
   - `CartControllerMergeTest`:
     - an empty account cart, merge 2 items → 2 lines
     - an equal line with quantity 2 vs guest 1 → stays 2; vs guest 3 → 3
     - the same merge twice → identical cart
     - a past check-in item and an unknown rate plan → `skipped` with their indexes and messages, the others added
     - more than `maxItems` items → 400
     - merged lines count in each other's rooms check
   - `CartExpiryTest`:
     - `cartItem(...)` lines with check-ins before and on `SearchCriteria.today()`
     - `removeExpiredItems()` removes only the earlier one and returns at least 1 (the database is shared)
     - GET before the call shows that line `CHECK_IN_PASSED`
5. Run `-Dtest='Cart*'`, then the whole `./mvnw verify` in phase 4.

## Success Criteria

- [x] Every phase 3 test passes. Phase 2's cart tests still pass unchanged.
- [x] In the test profile no scheduled cart job runs (`cron: "-"`), and the job is wired in `local` (seen in the
      startup log or with one manual tick).

## Risk Assessment

- **Merging two lines on a date edit**: delete the source and update the target in one flush. The target's key is
  unchanged, so `cart_items_line_key` can't trip over Hibernate's insert-before-delete flush order.
- **An exception caught inside the merge transaction**: `BusinessException` is thrown by our code before any SQL
  fails, so the transaction isn't marked rollback-only. Never catch a `DataIntegrityViolationException` there. The
  lock makes duplicates impossible, and if one ever happened it should fail the request (409), not be skipped.
- **Clock edges**: `SearchCriteria.today()` changes at 12:00 UTC. A line can turn `CHECK_IN_PASSED` between a read
  and an edit; the edit then fails with 400 "Check-in cannot be in the past" if it keeps those dates.
