---
phase: 2
title: Priced cart and add to cart
status: completed
priority: P1
dependencies:
  - 1
---

# Phase 2: Priced cart and add to cart

## Overview

`GET /api/cart` returns the cart grouped by hotel and date range, priced live by the hotel page's room list query
(C-CART-02). `POST /api/cart/items` adds a line or adds to an equal one (C-CART-01). Both share one "price these
lines" step and one "check this line" step, which phase 3 reuses.

## Context

- `plan.md` "Planner Defaults": statuses, notice, errors, limits, locking
- `hotel/services/impl/HotelServiceImpl.rooms` / `roomOptions`: how the room list is priced and decorated
  (`findRoomRates`, `findOfferDetails`, `findCancellationRules`, `findPhotos`, `taxRules.findCountriesWithTaxesOnTop`)
- `hotel/repository/impl/HotelRepositoryImpl.ROOM_RATES` / `findRoomRates`: the only "can sell the stay" plus "fits
  one room" definition. Call it, don't copy it. Its parameters are hotel, dates and guests per room; `rooms` is unused.
- `hotel/mapper/HotelMapper.toRate`: cancellation and price per room
- `search/model/SearchCriteria.of` / `today()`; `search/config/SearchProperties`
- `wishlist/controller/WishlistController` (`userId(jwt)`), `wishlist/services/impl/WishlistServiceImpl` (cards by id)
- `common/ratelimit/RateLimitPolicy.SEARCH`, `common/dto/Money`

## Requirements

- Functional: the endpoints and rules in `plan.md`. Responses:

  ```text
  CartResponse   { groups: [CartGroup] }
  CartGroup      { hotel: CartHotel, checkIn, checkOut, nights, taxesIncluded, canCheckout, total: Money|null,
                   items: [CartItemResponse] }
  CartHotel      { id, slug, name, starRating, coverPhotoUrl, city, countryCode, listed }   -- from HotelCardView
                                                                                        + listing flag
  CartItemResponse { id, ratePlanId, ratePlanName, roomTypeId, roomTypeName, photoUrl, boardType, boardTypeName,
                   quantity, adults, childAges, status, roomsLeft|null, cancellation: RateCancellation|null,
                   pricePerRoom: Money|null, totalPrice: Money|null, priceAtAdd: Money, priceChanged }
  CartItemRequest  { @NotNull ratePlanId, @NotNull checkIn, @NotNull checkOut, @NotNull @Min(1) quantity,
                   @NotNull @Min(1) adults, childAges: List<@NotNull @Min(0) @Max(17) Integer> (null → []) }
  ```

  `pricePerRoom` covers the whole stay for one room, and `totalPrice` = `pricePerRoom × quantity`. They are null
  (and so is `cancellation`) when the line isn't priced: `UNAVAILABLE` or `CHECK_IN_PASSED`.
- Non-functional:
  - a read costs one lines query, one `findRoomRates` per distinct (hotel, dates, guests per room), offer details,
    cancellation rules, photos, cards and listing flags (one each), and one tax query per group
  - an empty cart costs only the lines query

## Architecture

```text
CartController (/api/cart) ──► CartService ──► CartServiceImpl (@Transactional(readOnly = true) on the class)
                                                 cart(userId)            read path below
                                                 add(userId, request)    @Transactional
                                                   ├─ CartRepository.findByUserIdForUpdate / saveAndFlush(create)
                                                   ├─ CartItemRepository, RatePlanRepository.findSeller
                                                   └─ HotelRepository.findRoomRates (via the line check)
                                               CartMapper (@Component): groups, lines, Money
```

The read path, `cart(userId)`:

1. `lines = cartItems.findLines(userId)`. If empty, return `new CartResponse(List.of())`.
2. `today = SearchCriteria.today()`. Lines with `checkIn < today` → `CHECK_IN_PASSED`.
3. Group the remaining lines by `PriceKey(hotelId, checkIn, checkOut, adults, children)`. Per key, call
   `hotels.findRoomRates(hotelId, new StayRequirement(checkIn, checkOut, 1, adults, children))` and index the rows
   by `ratePlanId`.
4. For each line, `row = rates[key][ratePlanId]`:
   - no row, or `!row.fitsGuests()` → `UNAVAILABLE`
   - else the needed rooms = the sum of `quantity` over the group's lines (same hotel and dates) with this
     `roomTypeId` that have a row. If `needed > row.roomsLeft()` → `NOT_ENOUGH_ROOMS`
   - else → `AVAILABLE`
   - `pricePerRoom = row.stayPriceMinor()` in `hotelCurrency`
   - `priceChanged` = minor amount or currency differ from `priceAtAdd`
5. Decorations for every line, priced or not:
   - `ratePlans.findOfferDetails(ids)`: names and board; it doesn't filter by status
   - `findCancellationRules(priced ids)` and `hotelMapper.toRate(row, offer, rules, criteria, currency, now)`, with
     one `now` per read, for the cancellation terms
   - `roomTypes.findPhotos(roomTypeIds)`, first url
   - `hotels.findCardsByIdIn(hotelIds)` (no listing filter), plus which hotels are listed for `CartHotel.listed`:
     one JPQL `select h.id ... where h.id in :ids and <HotelJpql.HOTEL_LISTED>` added to `HotelRepositoryCustom`
     as `findListedIds`, or a derived query
   - `taxesIncluded` per group, as `HotelServiceImpl.rooms` computes it
6. The mapper builds the groups:
   - order: check-in, hotel name, hotel id; lines by item id
   - `canCheckout` = every line `AVAILABLE`
   - `total` = the sum of `AVAILABLE` lines' `totalPrice`, null when there are none

`toRate` needs a `SearchCriteria`. Build it with
`new SearchCriteria(checkIn, checkOut, 1, adults, childAges)` (the record's canonical constructor, not `of`: a
stored line isn't validated again on read).

The line check, `checkLine(List<CartItem> lines, seller, stay, guests, quantity, Set<Long> excludeIds)`. It is a
private method, and phase 3 reuses it as is. `lines` is the in-memory list of the locked cart's lines, so a merge
sees the lines it has just added:

1. `criteria = SearchCriteria.of(checkIn, checkOut, quantity, adults × quantity, childAges repeated quantity times,
   today, limits)`. This gives 400 for every stay and limit rule the search has, and `adultsPerRoom` /
   `childrenPerRoom` come out exactly per room.
2. `row = findRoomRates(seller.hotelId(), stay per room)[seller.ratePlanId()]`:
   - absent → 409 "This rate can't be booked for these dates"
   - `!fitsGuests` → 400 "This room can't hold N adults and M children"
3. `needed = quantity + Σ quantity` of the other `lines` (id not in `excludeIds`) with the same hotel, dates and
   `roomTypeId`. If `needed > roomsLeft` → 409 "Only N rooms of this type are left for these dates".
4. Return `row`, so the caller has the per-room price.

`add(userId, request)`:

1. Find the seller with `findSeller(ratePlanId)`, or throw `ResourceNotFoundException("Rate plan", id)`.
2. Lock the cart: `findByUserIdForUpdate(userId)`, or `saveAndFlush(Cart.create(userId))`.
3. Load the cart's items with `findByCartIdOrderByIdAsc`. Look for an `existing` line with the same key (ages
   sorted).
4. If there is no `existing` line and the cart already has `maxItems` lines → 409 "The cart can hold at most N
   room choices".
5. `row = checkLine(items, ..., quantity = existing.q + request.q (or request.q), excludeIds = {existing.id} (or
   empty))`.
6. Save:
   - `existing` → `changeQuantity(sum)` and `rebasePrice(row.stayPriceMinor, hotelCurrency)`
   - else `saveAndFlush(CartItem.create(...))`
7. `return cart(userId)`.

`POST /api/cart/items` answers 200 with `CartResponse`. `GET /api/cart` answers 200 with `CartResponse`. There is
no SecurityConfig change: every route outside the public matchers already needs a token, and OpenAPI's default
bearer requirement applies.

`RateLimitPolicy.SEARCH` becomes an `OrRequestMatcher` of `get("/api/search/hotels", "/api/search/hotels/filters",
"/api/hotels/*/rooms", "/api/cart")`, `post("/api/cart/items", "/api/cart/merge")` and
`put("/api/cart/items/*")`. Merge and PUT have no handler until phase 3, so their matchers are harmless until then.

## Related Code Files

- Create: `cart/controller/CartController.java`
- Create: `cart/dto/{CartResponse, CartGroup, CartHotel, CartItemResponse, CartItemRequest, CartItemStatus}.java`
- Create: `cart/mapper/CartMapper.java`
- Create: `cart/services/CartService.java`, `cart/services/impl/CartServiceImpl.java`
- Modify: `hotel/repository/HotelRepositoryCustom.java` + `impl/HotelRepositoryImpl.java` (`findListedIds`, JPQL),
  unless a derived query does it
- Modify: `common/ratelimit/RateLimitPolicy.java` (cart paths in `SEARCH`, plus a `put(...)` helper)
- Create tests:
  - `cart/controller/CartControllerTest.java` (`@IntegrationTest`)
  - `cart/controller/CartControllerPricesTest.java` (`@IntegrationTest`)
  - `cart/services/impl/CartServiceImplTest.java` (Mockito)
- Create test: `common/ratelimit/RateLimitPolicyTest.java`. No such test exists yet. It is a plain unit test with
  `MockHttpServletRequest`: the four cart requests match `SEARCH` plus `GENERAL`, and `DELETE /api/cart/items/1`
  matches only `GENERAL`.

## Implementation Steps

1. DTOs and the `CartItemStatus` enum (`AVAILABLE`, `NOT_ENOUGH_ROOMS`, `UNAVAILABLE`, `CHECK_IN_PASSED`). Give
   them class docs in the style of `RoomOption`/`RateOption`: per room, currency, what null means.
2. `CartMapper` builds `CartItemResponse` and `CartGroup` from the views and rows. It sorts groups and computes
   `total` and `canCheckout`. It uses `Money.ofMinor`.
3. `CartServiceImpl.cart` (read path), then `checkLine`, then `add`. Comment why the same line is summed on add (an
   explicit second pick) and why `checkLine` counts the group's other lines (one group is one booking).
4. `CartController`: `GET` and `POST /items`, with `@Valid @RequestBody` and `userId(jwt)`, like the wishlist. Keep
   the Javadoc short: what each answers and its error codes.
5. `RateLimitPolicy.SEARCH` matcher.
6. Tests: write them, then run the narrowest first (`-Dtest='Cart*'`).
   - `CartControllerTest`:
     - no token → 401
     - add, then GET → one group with the line's fields and status `AVAILABLE`
     - the same line twice → one line, quantities summed
     - two hotels and two date ranges → three groups, ordered by check-in
     - ages `[9,4]` and `[4,9]` are one line
     - unknown rate plan → 404
     - past check-in, check-out ≤ check-in, quantity > `maxRooms` → 400
     - guests the room can't hold → 400
     - a closed night or a stay shorter than min nights → 409
     - quantity > rooms left → 409; two lines of one room type (BB + RO plans) together over `roomsLeft` → 409 on
       the second
     - the 21st line → 409
     - user B's GET doesn't show user A's lines
     - after any rejected add, GET shows the cart unchanged
   - `CartControllerPricesTest`:
     - a `nightPrice` override after the add → `priceChanged: true`, with old and new prices
     - inventory lowered below the quantity → `NOT_ENOUGH_ROOMS` and `canCheckout: false`
     - a closed night, an inactive plan or `hotelStatus(INACTIVE)` → `UNAVAILABLE`, prices null, `hotel.listed`
       false for the unlisted case
     - a line from `cartItem(...)` with a past check-in → `CHECK_IN_PASSED`
     - a cart line's `pricePerRoom` equals `/api/hotels/{slug}/rooms`'s `totalPrice` for the same rate, stay and
       guests (keeps the two paths equal)
     - `total` sums only the `AVAILABLE` lines
   - `CartServiceImplTest` (Mockito):
     - an empty cart calls nothing but `findLines`
     - `CHECK_IN_PASSED` lines don't call `findRoomRates`
     - two lines with one (hotel, dates, guests) cause one `findRoomRates` call
     - `NOT_ENOUGH_ROOMS` counts only the lines of the same room type in the same group

## Success Criteria

- [x] `-Dtest='Cart*'` passes. `HotelControllerRoomsTest`, `SearchController*Test` and `WishlistControllerTest` are
      unchanged and pass.
- [x] `ROOM_RATES`, `RATE_OFFERS` and `night_price()` are untouched (`git diff` shows no change in
      `HotelRepositoryImpl`'s SQL text blocks).

## Risk Assessment

- **Hotel currency change**: lines on a plan in the old currency drop out of `findRoomRates` (it requires
  `rp.currency = h.currency`), so they read `UNAVAILABLE`. That's correct, and no conversion is needed.
- **Reading `childAges` back from the view**: `select new` with an array column may arrive as `Short[]`. Convert it
  in the record's compact constructor, and test it in `CartItemRepositoryTest`.
- **Cost**: at most `maxItems` `findRoomRates` calls per read, worst case 20 lines with 20 different stays. That is
  fine for a cart, and the README notes it.
- **Shared `search` bucket**: a user editing a big cart and searching shares 60/min. That's acceptable, and a
  separate `cart` limit is a one-line change later.
