# Features and API

Every endpoint, the rules behind each feature, curl examples, and the gaps left in on purpose. Setup and running are in
the [README](../README.md); code layout, tests and configuration are in [development.md](development.md).

This project holds the first features of the Hotel Booking Platform described in
[USER_STORIES.md](../USER_STORIES.md): the homepage's location autocomplete, hotel availability search and popular
destinations (C-HOME-01/02/03), the priced hotel result list (C-SRCH-01) with its filters, filter counts and sorting (C-SRCH-02/03), plus the admin endpoints that
curate those destinations. See [Search and destinations](#search-and-destinations). Customer accounts
(C-AUTH-01/02/04/05/06) have email verification, sign-in sessions per device, lockout, password reset and a profile.
See [Accounts and sessions](#accounts-and-sessions). Signed-in customers can save hotels to a wishlist (C-SRCH-08).
See [Wishlist](#wishlist). Each hotel has a public page: overview, facilities and policies, the rooms and rates for a
stay with their cancellation penalties, and reviews (C-HOTEL-01/02/03/04/06/07). See
[Hotel detail page](#hotel-detail-page). Signed-in customers keep a cart of rooms from any hotels and dates, priced
again on every read and grouped into future bookings, and a guest's browser cart merges into it on sign-in
(C-CART-01/02/03/04). See [Cart](#cart). A cart group is checked out into one booking: a quote with the price
breakdown, a discount code and the taxes, then a Pending booking that holds its rooms for 15 minutes while the
customer pays (C-BOOK-01..06). See [Bookings and checkout](#bookings-and-checkout). The customer pays by card
through Stripe, and only Stripe's signed webhook confirms the booking; a declined card can be retried until the hold
ends. The confirmed booking has a page and emails for the customer and the hotel. A paid booking can be cancelled
for a refund to the card by its policy (C-PAY-01/02/03/05, C-MYBK-03). See [Payment and cancellation](#payment-and-cancellation).

## Endpoints

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/api/auth/register` | public | 201, unverified; `{email, password, confirmPassword, fullName, phone?}`; 409 if taken; logs the verification link |
| POST | `/api/auth/verify-email` | public | `{token}` from the link → 204; 400 unknown, used or expired link |
| POST | `/api/auth/verify-email/resend` | public | `{email}` → always 202; a new link only for an unverified account |
| POST | `/api/auth/login` | public | `{email, password, rememberMe?}` → 200 `{accessToken, tokenType, expiresIn, refreshToken, refreshExpiresIn}`; 401 on any failure (also when locked); 403 email not verified |
| POST | `/api/auth/refresh` | public | `{refreshToken}` → 200 new pair; 401 unknown, used, expired or revoked |
| POST | `/api/auth/logout` | public | `{refreshToken}` → always 204; ends that device's session |
| POST | `/api/auth/logout-all` | authenticated | 204; ends every session of the user |
| POST | `/api/auth/password/forgot` | public | `{email}` → always 202; logs a reset link for an existing account |
| POST | `/api/auth/password/reset` | public | `{token, newPassword}` → 204, every session ended; 400 unknown, used or expired link |
| GET | `/api/auth/me` | authenticated | current user's profile |
| PUT | `/api/auth/me` | authenticated | `{fullName, phone?, countryCode?, preferredCurrency?}` replaces the profile |
| PUT | `/api/auth/me/password` | authenticated | `{currentPassword, newPassword}` → 204, other sessions ended; 400 wrong current password |
| GET | `/api/products` | authenticated | paged: `{content: [...], page: {size, number, totalElements, totalPages}}`; max size 100 |
| GET | `/api/products/{id}` | authenticated | 404 if missing |
| POST | `/api/products` | ADMIN | 201 + `Location` |
| PUT | `/api/products/{id}` | ADMIN | |
| DELETE | `/api/products/{id}` | ADMIN | 204 |
| GET | `/api/search/suggestions?q=&limit=` | public | `{destinations: [...], hotels: [...]}`; `q` 2–100 chars; `limit` per list, max 10 |
| GET | `/api/search/hotels?destination=\|hotel=&checkIn=&checkOut=&rooms=&adults=&childAges=` + filters, `sort` | public | paged; hotels that pass the filters and can sell the stay, each with its cheapest allowed offer; 400 invalid criteria or sort, 404 unknown slug |
| GET | `/api/search/hotels/filters?` (same parameters, no paging) | public | every filter option with its hotel count, price range per night |
| GET | `/api/destinations/popular?limit=&hotels=` | public | featured destinations with their best hotels; max 20 × 10 |
| GET | `/api/destinations/{slug}` | public | 404 if missing |
| GET | `/api/hotels/{slug}` | public | overview, gallery, address, facilities, policies; 404 unknown or unlisted hotel |
| GET | `/api/hotels/{slug}/rooms?checkIn=&checkOut=&rooms=&adults=&childAges=` | public | sellable room types with every rate, rooms left and cancellation penalties; 400 invalid stay, 404 as above |
| GET | `/api/hotels/{slug}/reviews?page=&size=&sort=` | public | paged published reviews, newest first; `sort` by `createdAt` or `score`, else 400; 404 as above |
| GET | `/api/wishlist?page=&size=` | authenticated | paged saved hotels as cards, newest first, each with `available` and `savedAt` |
| PUT | `/api/wishlist/{hotelId}` | authenticated | 204 whether new or already saved; 404 unknown or unlisted hotel |
| DELETE | `/api/wishlist/{hotelId}` | authenticated | always 204 |
| GET | `/api/cart` | authenticated | `{groups: [...]}`: lines by hotel and dates, each with its status and current price |
| POST | `/api/cart/items` | authenticated | `{ratePlanId, checkIn, checkOut, quantity, adults, childAges?}` → the cart; 400 invalid stay or guests, 404 unknown rate plan, 409 not sellable, too few rooms, cart full |
| PUT | `/api/cart/items/{id}` | authenticated | `{checkIn, checkOut, quantity}` → the cart; errors as for adding, 404 not in this cart |
| DELETE | `/api/cart/items/{id}` | authenticated | always 204 |
| POST | `/api/cart/merge` | authenticated | `{items: [...]}` (the browser cart) → `{cart, skipped}`; 400 more items than a cart holds |
| POST | `/api/bookings/quote` | authenticated | `{cartItemIds, discountCode?}` → hotel, lines, `price` (subtotal, discount, taxes, total) and the booker from the profile; 400 lines of several hotels or dates, or a refused code; 404 not in the cart; 409 can't be booked now |
| POST | `/api/bookings` | authenticated | `{cartItemIds, discountCode?, expectedTotal, booker, guestName?, specialRequests?, estimatedArrival?, leadGuests?, acceptedTerms}` → 201 Pending booking; errors as for a quote, plus 400 invalid guest details or terms not accepted, 409 total changed or rooms just taken |
| GET | `/api/bookings/{reference}` | authenticated | your booking: hotel address and contact, rooms, terms, `price`, `payment`, `cancellation` (with the refund status); 404 not yours |
| POST | `/api/bookings/{reference}/payment` | authenticated | → `{clientSecret, amount, status, paymentStatus, payBy}` for Stripe's Payment Element, the same intent on every call; 404 not yours; 409 not Pending, hold ended, or payment being processed; 503 Stripe unavailable |
| GET | `/api/bookings/{reference}/payment` | authenticated | `{bookingStatus, payBy, amount, payment: {status, failureReason, paidAt}}`, polled after Stripe.js returns |
| GET | `/api/bookings/{reference}/cancellation` | authenticated | `{paid, penalty, refund, asOf}` if cancelled now; 404 not yours; 409 not Confirmed |
| POST | `/api/bookings/{reference}/cancel` | authenticated | `{expectedRefund, reason?}` → the Cancelled booking; 409 refund changed since the preview, or not Confirmed |
| POST | `/api/payments/stripe/webhook` | public (Stripe signature) | Stripe's events; 400 bad signature; not in the API docs |
| GET | `/api/admin/destinations?q=` | ADMIN | paged; name prefix, featured first |
| PUT | `/api/admin/destinations/{id}` | ADMIN | `{name, imageUrl, featuredRank}`; `featuredRank: null` = not featured |
| POST | `/api/admin/destinations/sync` | ADMIN | runs the destination sync now: `{created, withHotels}` |
| GET | `/actuator/health`, `/actuator/info` | public | |
| GET | `/swagger-ui.html`, `/v3/api-docs` | public | disabled in the `prod` profile |

Every error is `application/problem+json` (RFC 9457):

```json
{"title": "Bad Request", "status": 400, "detail": "Validation failed", "instance": "/api/products",
 "errors": [{"field": "price", "message": "must be greater than or equal to 0.00"}]}
```

**Rate limits.** Every request is limited per client IP (an IPv6 client by its /64). Some endpoints have a stricter
limit on top. Endpoints listed together share one count:

| Limit | Requests | Per client IP |
|-------|----------|---------------|
| `general` | every request except `/actuator/health` | 300 per minute |
| `login` | `POST /api/auth/login` | 10 per minute |
| `register` | `POST /api/auth/register` | 10 per hour |
| `email` | `POST /api/auth/verify-email/resend`, `POST /api/auth/password/forgot` | 10 per hour |
| `search` | `GET /api/search/hotels`, `/api/search/hotels/filters`, `/api/hotels/{slug}/rooms`, `/api/cart`; `POST /api/cart/items`, `/api/cart/merge`; `PUT /api/cart/items/{id}` | 60 per minute |
| `checkout` | `POST /api/bookings/quote`, `POST /api/bookings`, `POST /api/bookings/{reference}/payment` | 20 per minute |

A request under a specific limit also counts toward `general`. Counts come back gradually, not all at once: after 10
sign-ins, one more is allowed every 6 seconds. Over a limit, the API answers `429` problem+json
`"Too many requests, retry later"` with a `Retry-After` header (seconds). The check runs before the token, the body
and the password are checked, and after CORS, so browser code can read the 429.

## Try it with curl

```bash
BASE=http://localhost:8080

# register (role USER); the account cannot sign in until its email is verified
curl -s -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"jane-password-1","confirmPassword":"jane-password-1","fullName":"Jane Doe"}' \
  $BASE/api/auth/register

# no email is sent yet: the app log has the link, e.g.
#   Email verification link for jane@example.com: http://localhost:5173/verify-email?token=AbC...
curl -s -H 'Content-Type: application/json' -d '{"token":"AbC..."}' $BASE/api/auth/verify-email

# login → access token (15 min) + refresh token
LOGIN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"jane-password-1","rememberMe":true}' $BASE/api/auth/login)
TOKEN=$(echo "$LOGIN" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
REFRESH=$(echo "$LOGIN" | sed -E 's/.*"refreshToken":"([^"]+)".*/\1/')

curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/auth/me

# new pair when the access token expires; the old refresh token stops working
curl -s -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$REFRESH\"}" $BASE/api/auth/refresh

# sign out this device (use the newest refresh token)
curl -s -H 'Content-Type: application/json' -d '{"refreshToken":"<newest>"}' $BASE/api/auth/logout
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/products?page=0&size=20&sort=name,asc"

# creating products needs ADMIN: log in as APP_BOOTSTRAP_ADMIN_EMAIL instead, then
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Coffee beans","description":"1kg bag","price":19.99}' $BASE/api/products
```

## Accounts and sessions

**Sign-up.** Passwords need 8–72 characters with a letter and a digit (and at most 72 bytes). A new account starts
unverified, and a verification link (24 h, works once) is sent. **No email service is wired yet: links are written
to the app log** (`AuthMailServiceImpl`). Links point to the web app (`APP_FRONTEND_URL`): `/verify-email?token=…`
and `/reset-password?token=…`. That page posts the token to the API. Signing in before verifying gets 403 "Email not
verified", but only with the right password. A wrong password always gets the generic 401. Existing accounts and the
bootstrap admin count as verified.

**Sessions.** Sign-in returns a 15-minute access token and a refresh token. Each sign-in is one session (one device),
stored with its user agent and IP; only a SHA-256 hash of the refresh token is kept. A session lasts 1 day, or 30 days
with `rememberMe`, counted from sign-in; refreshing never extends it.

- `POST /api/auth/refresh` hands out a new pair, and the old refresh token stops working.
- Presenting an already-used refresh token again ends the session: it was probably stolen. Clients must therefore
  send **one refresh at a time** per device (two tabs refreshing at once sign that device out).
- Every access token carries its session id (`sid`), and each request checks that the session is live and the user
  enabled. Sign-out, sign-out everywhere, a password change and a password reset take effect at once, not when the
  access token expires.
- The public account endpoints ignore the `Authorization` header, so a client holding an expired access token can
  still refresh, sign in or sign out.

**Lockout.** 5 failed sign-ins within 15 minutes (counted from the first failure) lock the account for 15 minutes.
A locked account gets the same 401 as a wrong password, and its password is not checked. A successful sign-in or a
password reset clears the count.

**Passwords.** `POST /api/auth/password/forgot` always answers 202. For an existing account it sends a reset link
(30 min, works once; asking again kills the previous link). Resetting signs out every device, lifts a lockout, and
marks the email verified. `PUT /api/auth/me/password` needs the current password and signs out every other device.
At most one verification or reset email per account per minute (`app.auth.email-cooldown`).

**Profile.** `PUT /api/auth/me` replaces name, phone, nationality (`countryCode`, ISO 3166 alpha-2) and
`preferredCurrency` (ISO 4217). Fields left out are cleared, and codes are stored uppercase.

## Search and destinations

The homepage endpoints are public: guests search without an account.

**Destinations** (countries, cities, districts, wards) are not entered by hand. The destination sync builds them
from the addresses of listed hotels (status `ACTIVE`, not deleted) and refreshes each one's `hotelCount`. It runs
when the app is ready, on `APP_DESTINATIONS_SYNC_CRON` (every 10 minutes by default), and on
`POST /api/admin/destinations/sync`. Places whose hotels all went away keep their row with a count of 0, which hides
them from autocomplete. Each place stores the most common spelling of its address parts among its hotels (the sync
updates it when another spelling takes over). Admins own the display name, image and featured rank, and the sync
never overwrites them.
Slugs come from the place names without accents: `vn`, `vn-da-nang`, `vn-da-nang-hai-chau`.

**Matching ignores accents, case and extra spaces**: `da nang` finds "Đà Nẵng". The database function `norm()`
(`unaccent` + lowercase) is the only normalizer. A place is suggested when its name is similar to the typed text
(pg_trgm word similarity, 0.6 by default), so a half-typed word (`hai ch`) or a small typo (`hai chua`) still finds
Hải Châu. The closest name comes first, then the place with more hotels, so a city comes before the districts
inside it. Only the name is matched for now: typing a place together with its city (`hai chau da nang`) is planned
for later (C-HOME-07). The label under a suggestion says where the place is (`Hải Châu, Đà Nẵng, Vietnam`). Hotel
suggestions still need a word of the hotel's name to start with the typed text.

**Search criteria** (all GET parameters, so a search can be shared or bookmarked):

| Parameter | Rule |
|-----------|------|
| `destination` or `hotel` | exactly one: a destination slug or a hotel slug from the suggestions |
| `checkIn`, `checkOut` | ISO dates; check-in not in the past and at most 365 days ahead; check-out after check-in; at most 30 nights |
| `rooms` | 1–8, and at most `adults` (every room needs an adult) |
| `adults` | 1–30 |
| `childAges` | one per child, 0–17, repeated (`childAges=4&childAges=9`); at most 10 |

A hotel is returned when its `ACTIVE` room types that fit the guests have, together, at least `rooms` rooms free on
every night. Guests are split evenly: 3 adults and 1 child in 2 rooms need rooms for 2 adults and 1 child. A room type
fits when `max_adults` covers the adults per room, `max_occupancy` covers everyone, and the children fit in
`max_children` plus the adult places left free. A night without a `room_inventory` row has the room type's
`total_rooms` free, and a room only counts when one of its rate plans can sell the whole stay (see Prices).
Hotels that can't are hidden, not shown as sold out. Without `sort`, results are ordered by user rating (best
average first, then most reviews); `page.totalElements` is the result count ("124 hotels in Đà Nẵng", the name coming from the suggestion or `/api/destinations/{slug}`).

**Prices (C-SRCH-01).** Each result carries `offer`, the cheapest way the hotel can host the stay:

- A room type is sold through rate plans (`rate_plans`: board type, cancellation policy, base price per room per
  night). A plan can sell the stay when it is active, priced in the hotel's currency (`hotels.currency`), has no
  closed night (`rate_plan_prices.is_closed`) and the stay is at least as long as the longest minimum stay of its
  nights (`coalesce(rate_plan_prices.min_nights, rate_plans.min_nights)`).
- A night's price comes from the SQL function `night_price()`, the only definition of it: the owner's price for that
  date (`rate_plan_prices`), else the active `rate_rules` row with the highest priority (then the newest) covering
  the date and weekday (set a price, +/- an amount, +/- a percent of the base price), else the base price.
- Each room type uses its cheapest plan, and rooms are taken from the cheapest room type up. `roomLines` has one
  line per room type used, so a 2-room search can show "1 × Deluxe + 1 × Family Suite" when only one Deluxe is free.
- Money is `{"amount": 2400000, "currency": "VND"}`: major units, with the currency's ISO 4217 decimals
  (`"amount": 129.50` for USD). The database keeps whole minor units (`*_minor`).
- `taxesIncluded` is true when no active `tax_rules` row of the hotel's country, valid on a night of the stay, adds
  tax on top of the price (a country without rules counts as included). The amounts come with the checkout breakdown.
- Each line has `cancellation`: `freeUntil` is check-in day at `hotels.check_in_from` in `hotels.timezone`, minus
  the largest `hours_before_check_in` among the policy's rules with a penalty; it is null for a non-refundable policy
  or once that moment has passed. The client shows "Free cancellation until 12 Oct", the policy name, or
  "Non-refundable".
- `photoUrls` lists up to 10 photos of the hotel itself, cover first. A card links to the hotel by `slug`, with the
  same query parameters.

There are no write endpoints for rates, policies or taxes yet: the local demo seed adds two plans per room type
(room only with free cancellation; breakfast, non-refundable, 10% less), a +20% Friday/Saturday rule, one date
override and Vietnam's VAT on top.

**Filters (C-SRCH-02).** All optional GET parameters; lists are repeated (`stars=4&stars=5`):

| Parameter | Matches |
|-----------|---------|
| `name` | part of the hotel name, ignoring accents and case (`%` and `_` match only themselves) |
| `stars` | any of these star ratings (1–5) |
| `minGoogleRating`, `minUserRating` | at least this Google rating (0–5) / user rating (0–10) |
| `accommodationTypes` | any of these codes: `hotel`, `resort`, `apartment`, `villa`, `hostel` |
| `facilities` | all of these hotel facilities (`amenities` with scope `HOTEL`: `wifi`, `pool`, `parking`, ...) |
| `maxDistanceM` | at most this many metres from the centre (`hotels.distance_to_center_m`) |
| `boardTypes` | any of `RO`, `BB`, `HB`, `FB`, `AI` |
| `cancellation` | `FREE` (free cancellation still possible for this stay) and/or `NON_REFUNDABLE` |
| `bedTypes` | a room with any of `single`, `double`, `queen`, `king`, `twin` (`room_type_beds`) |
| `roomFeatures` | a room with all of these features (`amenities` with scope `ROOM`: `balcony`, `sea_view`, ...) |
| `minPrice`, `maxPrice` | price per night of the whole offer (all rooms), inclusive, in `currency` |
| `currency` | ISO 4217 code prices are compared in; default: the selling currency of most listed hotels in the destination (for a hotel search, its own) |

The first six describe the hotel and drop the hotels that don't match. Board type, cancellation, bed type and room
features describe the room and its rate. They limit which rate plans and room types the offer may use, so with
`boardTypes=BB` the card shows the cheapest breakfast offer, and a hotel without one is hidden. The price range then
checks that offer: `total >= minPrice × nights` and `total <= maxPrice × nights`. A refundable rate whose free
cancellation has already ended is neither `FREE` nor `NON_REFUNDABLE`. Hotels without a star rating, rating or
distance never match those filters. Unknown codes match nothing. There is no currency conversion yet (C-SRCH-05):
a hotel selling in another currency than `currency` never matches a price range.

**Filter counts.** `GET /api/search/hotels/filters` takes the same parameters (without paging and sort). It answers
every option of every filter with the number of hotels the list would show if that option were picked, so a client
can show "4 stars (12)" and keep the selection in the URL. Each list is named after its query parameter (`stars`,
`minGoogleRating`, `minUserRating`, `accommodationTypes`, `facilities`, `maxDistanceM`, `boardTypes`,
`cancellation`, `bedTypes`, `roomFeatures`):

- In an "any of" list (stars, accommodation types, board types, cancellation, bed types), a count ignores that
  list's own selection: picking 5★ leaves the 4★ count as it was. Other filters still apply.
- In an "all of" list (facilities, room features), a count is the current results that also have the option.
- Numeric options count each fixed value on its own: Google 3.0/3.5/4.0/4.5, users 6/7/8/9, distance 1/3/5/10 km.
- Board, cancellation, bed and room-feature counts price each option the way the list would, so they respect the
  price range.
- Every option is listed, even with 0 hotels. Reference options carry their `name`; facilities and room features
  also have a `category`. `priceRange` holds the lowest and highest price per night among the results, ignoring
  the price filter itself (null when none sells in `currency`).

**Sorting (C-SRCH-03).** `sort=price` (cheapest offer first; hotels selling in another currency than `currency`
come last), `sort=rating` (best user rating first among hotels with at least `app.search.rating-sort-min-reviews`
reviews, 10 by default, then the others), or `sort=distance` (nearest first, unknown distance last). Any case works,
and the direction is ignored. An unknown value or several sorts give 400. Without `sort` the order is the user-rating
order above. A "recommended" sort is not implemented yet.

**Popular destinations** are the ones an admin featured (`featuredRank`, lowest first), each with its best-rated
listed hotels. While nothing is featured, the cities with the most hotels are shown. Clicking one runs the default
search, which the client builds because only it knows the traveller's "tomorrow":
`/api/search/hotels?destination={slug}&checkIn={tomorrow}&checkOut={day after}&rooms=1&adults=2`.

**Last search (C-HOME-02) is kept by the browser; the server stores nothing.** A client saves the parameters of the
last search together with the label of the chosen suggestion, for example
`localStorage.lastSearch = {"destination": {"slug": "vn-da-nang", "name": "Đà Nẵng"}, "checkIn": "2026-11-01",
"checkOut": "2026-11-03", "rooms": 1, "adults": 2, "childAges": [7]}`. On the next visit it pre-fills the form, drops
the dates when check-in has passed, and checks a stored destination with `GET /api/destinations/{slug}` (404 means
forget it). The same call labels a search opened from a shared URL.

```bash
curl -s "$BASE/api/search/suggestions?q=da%20nang"
curl -s "$BASE/api/search/hotels?destination=vn-da-nang&checkIn=2026-11-01&checkOut=2026-11-03&rooms=1&adults=2"
curl -s "$BASE/api/search/hotels?destination=vn-da-nang&checkIn=2026-11-01&checkOut=2026-11-03&rooms=1&adults=2&facilities=pool&boardTypes=BB&sort=price"
curl -s "$BASE/api/search/hotels/filters?destination=vn-da-nang&checkIn=2026-11-01&checkOut=2026-11-03&rooms=1&adults=2&maxPrice=1300000"
curl -s "$BASE/api/destinations/popular"
curl -s -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Đà Nẵng","imageUrl":"https://example.com/da-nang.jpg","featuredRank":1}' \
  $BASE/api/admin/destinations/1
```

**Demo data.** Under the `local` profile Flyway also runs `db/seed/R__demo_catalog.sql`: 9 fictional hotels in Đà
Nẵng, Hồ Chí Minh, Hà Nội and Hội An (one inactive hotel in Huế), and those four cities featured. The hotels have
distances to the centre and facilities, and their room types have beds and room features. For the hotel page they
also have descriptions, map coordinates, gallery photos with categories (lobby, restaurant, pool), room sizes and
photos, and six policies each. Mỹ Khê's room-only rates use a two-tier cancellation policy (free until 72 hours before
check-in, then half the stay, then all of it in the last 24 hours). There are no reviews: nothing can write one yet.
It also adds two verified accounts with public passwords: `demo@example.com` / `demo-password-1` (customer) and
`demo-admin@example.com` / `demo-admin-password-1` (admin), used by [Try it in Swagger](../README.md#try-it-in-swagger).
The `test` and `prod` profiles never load it. `docker compose down -v` resets it.

## Wishlist

Signed-in customers keep one wishlist of hotels to compare and book later (C-SRCH-08). Every call needs an access
token, and the list is always the token's user's.

- **Saving** (`PUT /api/wishlist/{hotelId}`) answers 204 whether the hotel is new to the list or already in it.
  Saving again changes nothing and keeps the first `savedAt`, and two saves at once never conflict. Only listed
  hotels (`ACTIVE`, not deleted) can be saved; any other id gets 404.
- **Removing** (`DELETE /api/wishlist/{hotelId}`) always answers 204, also for a hotel that wasn't saved.
- **The list** (`GET /api/wishlist`) is paged, newest saved first; `sort` is ignored. Each item has the search card's
  hotel fields (`id`, `slug`, `name`, type, stars, ratings, place, distance) with one `coverPhotoUrl`, plus `savedAt`
  and `available`. A hotel that is no longer listed stays with `available: false`: it can't be opened or booked
  until it is listed again (then `available` turns back to `true`), and it can still be removed.
- **No prices.** They depend on dates and guests, so the list has none. To compare, the client opens a saved hotel
  with its last search (`/api/search/hotels?hotel={slug}&checkIn=...`).
- **Hearts on result cards.** Search results don't say which hotels are saved. The client reads the hotel `id`s from
  `GET /api/wishlist` (paging through with `size=100`), marks the result cards from them, and updates its copy when
  it saves or removes a hotel.

Deleting a user or a hotel row deletes its wishlist entries (`on delete cascade`).

```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" $BASE/api/wishlist/1
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/wishlist?page=0&size=20"
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" $BASE/api/wishlist/1
```

## Hotel detail page

A search result links to its hotel by `slug` with the search's query parameters. The page's three endpoints are
public and answer 404 for a hotel that doesn't exist or isn't listed (`ACTIVE`, not deleted).

**Overview** (`GET /api/hotels/{slug}`, C-HOTEL-01/02/03) needs no dates:

- Name, type, stars, description, Google and user ratings (the stored `user_rating_avg`/`_count`, as in search) and
  the distance to the centre.
- `address`: the parts, the coordinates for the map (both null when unknown) and `formatted`, the parts that exist
  from street to country: `120 Võ Nguyên Giáp, Phước Mỹ, Sơn Trà, Đà Nẵng, Vietnam`. The province is left out. The
  country name comes from the COUNTRY destination, like suggestion labels; until the sync has named it, the code
  stands in.
- `photos`: the gallery. Hotel photos come first (cover first, then `sort_order`), then the photos of room types on
  sale. Each has `category` (pool, lobby...; a room photo without one says `room`), `caption` and `roomTypeId`.
- `facilities`: the hotel's amenities grouped under their headings (General, Wellness...), each `free` or not.
- `policies`: `checkInFrom`, `checkInUntil`, `checkOutUntil` (wall-clock times in the hotel's zone), `minCheckInAge`
  and `rules`, the hotel's policy paragraphs in its order. Types: `CHILDREN`, `EXTRA_BED`, `PETS`, `SMOKING`,
  `DEPOSIT` (ID and deposit), `PAYMENT` (methods accepted at the property), `OTHER`. There is no write API for them
  yet.

**Rooms** (`GET /api/hotels/{slug}/rooms`, C-HOTEL-04) takes the search's stay parameters with the same rules and
400s, so changing the dates on the page (C-HOTEL-05) is calling it again. It lists every room type with a room free on
every night, each with every rate plan that can sell the stay. "Free" and "can sell" are the search's own SQL
(`HotelRepositoryImpl`), so sold-out room types and unsellable plans are not listed.

- `roomsLeft` is the fewest free rooms on any night, shared by all the room type's rates: the most a guest can pick
  of that room type.
- `fitsGuests` tells whether one room holds the guests of one room when they are split evenly, as in search. Room
  types that don't fit are listed last, not hidden, so a guest can still mix room types.
- Each room type has its size, description, beds, room features and photos. Rates are cheapest first, and room types
  follow their cheapest rate.
- Prices are per room, in the hotel's currency: `totalPrice` for the stay and `pricePerNight`, the average rounded
  half up. `taxesIncluded` follows the search rule. For one room, the cheapest fitting rate is the offer the search
  card shows.

**Cancellation** (C-HOTEL-06). Each rate has `refundable`, `policyName`, `freeUntil` (the card's deadline) and
`penalties`: what cancelling one room costs after that, in time order (`CancellationSchedule`).

- A rule "less than N hours before check-in costs X" applies from check-in minus N hours until the next smaller
  rule starts. The smallest rule also covers cancelling after check-in and a no-show, so its `until` is null.
  `from` is null for the penalty already in effect, and penalties that are over are left out.
- Amounts are capped at the stay. `PERCENT` is that share of the stay. `NIGHTS` is the first N nights, with a fraction
  of a night charged pro rata. `FIXED` is an amount in the rate's currency.
- A non-refundable rate has one penalty with `from` and `until` both null. A refundable rate without paid rules is
  free until check-in and has no penalties.

**Reviews** (`GET /api/hotels/{slug}/reviews`, C-HOTEL-07) are paged, published only and newest first. `sort=score`
or `sort=createdAt`, either direction, with ties going to the newest. Any other sort property gives 400. Each review
has `score` (1–10), `title`, `body`, `travellerType`, `createdAt` and the hotel's `ownerReply`. Reviews don't name
their author. Only customers with a completed stay will write them (C-MYBK-05), so until bookings exist the list is
always empty.

```bash
curl -s "$BASE/api/hotels/my-khe-beach-resort-demo"
curl -s "$BASE/api/hotels/my-khe-beach-resort-demo/rooms?checkIn=2026-11-01&checkOut=2026-11-03&rooms=1&adults=2"
curl -s "$BASE/api/hotels/my-khe-beach-resort-demo/reviews?sort=score,desc"
```

## Cart

Signed-in customers keep one cart (C-CART-01/02/03/04). Every call needs an access token, and the user always comes
from the token. Guests keep their cart in the browser, where the public `GET /api/hotels/{slug}/rooms` prices it.
After signing in, the client sends it once to `POST /api/cart/merge`.

- **Lines and groups.** A line is `quantity` rooms of one rate plan (room type + board + cancellation) for one stay,
  with the same `adults` and `childAges` in each room. The cart has one line per rate, stay and guests; the same ages
  in another order are the same line. Lines are grouped by hotel and date range, and a group is what one checkout
  will book as one booking (see [Bookings and checkout](#bookings-and-checkout)). Groups come by check-in, then hotel name. Lines come in the
  order they were added. A cart holds at most 20 lines (`app.cart.max-items`).
- **Live prices** (`GET /api/cart`, C-CART-02). Nothing is locked when a line is added. Every read prices each line
  with the hotel page's room list (one call per hotel, dates and guests per room), so the cart, the room list and the
  search agree on what can be sold and for how much. Each line has a `status`:
  - `AVAILABLE`.
  - `NOT_ENOUGH_ROOMS`: the rate still sells, but the group's lines of that room type need more rooms than
    `roomsLeft`.
  - `UNAVAILABLE`: the rate no longer sells the stay (sold out, a closed night, a minimum stay, or the plan, room
    type or hotel off sale), or the room type no longer holds the guests. `hotel.listed` is false when it's the
    hotel.
  - `CHECK_IN_PASSED`: the next expiry run deletes it.
- **What a line shows.**
  - A priced line (`AVAILABLE` or `NOT_ENOUGH_ROOMS`) has `roomsLeft`, `cancellation` (as on the hotel page),
    `pricePerRoom` (one room, whole stay) and `totalPrice`. Other lines have null there.
  - `priceAtAdd` is one room's price when the line was last picked, and `priceChanged` flags a difference.
- **What a group shows.** `canCheckout` is true when every line is `AVAILABLE`, `total` sums those lines, and
  `taxesIncluded` follows the search rule. Prices are in the hotel's currency, before any tax added on top.
- **Adding** (`POST /api/cart/items`) answers the cart. Adding a line that is already there adds up the quantities and
  resets `priceAtAdd`. A rejected add changes nothing. It answers:
  - 400 for a stay the search would reject (also for the summed quantity) or guests the room can't hold
  - 404 for an unknown rate plan
  - 409 when the rate can't sell the stay, too few rooms are left (counting the group's other lines of that room type)
    or the cart is full
- **Editing** (`PUT /api/cart/items/{id}`, C-CART-03) runs the same checks and answers the cart.
  - Changing only the quantity keeps `priceAtAdd`, so the notice survives. New dates reset it.
  - New dates that match another line of the same rate and guests move the rooms into that line.
  - 404 when the line isn't in this cart.
- **Removing** (`DELETE /api/cart/items/{id}`) always answers 204.
- **Merging** (`POST /api/cart/merge`, at most 20 items, else 400) adds each item like `POST /items`, with one
  difference: a line already in the cart keeps the larger quantity and its `priceAtAdd`. The same pick on two
  devices doesn't double the rooms, and sending the merge again changes nothing.
  - New lines start at the merge's current price.
  - Items that can't be added are skipped, and the answer is `{cart, skipped: [{index, reason}]}`.
- **Expiry** (C-CART-04): `CartExpiryJob` deletes lines whose check-in is before `SearchCriteria.today()`, hourly by
  default (`APP_CART_EXPIRY_CRON`). That date is the one at UTC−12, so a line goes only once its check-in has passed
  everywhere.
- Every write locks the user's `carts` row, so concurrent edits of one cart run one after the other. The first add
  creates the row; two first adds at the same moment give one 409.

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' $BASE/api/cart/items \
  -d '{"ratePlanId": 1, "checkIn": "2026-12-01", "checkOut": "2026-12-03", "quantity": 1, "adults": 2}'
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/cart
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' $BASE/api/cart/items/1 \
  -d '{"checkIn": "2026-12-02", "checkOut": "2026-12-04", "quantity": 2}'
```

## Bookings and checkout

A checkout books one cart group, the rooms of one hotel for one date range, as one booking paid once
(C-BOOK-01..06). It needs an access token, and it starts from the server cart. The client quotes the group, shows the
breakdown and the guest form, then books it.

- **Quote** (`POST /api/bookings/quote`, `{cartItemIds, discountCode?}`). It writes nothing and returns:
  - the hotel and the stay
  - each line: rate, board, quantity, guests, `pricePerRoom`, `subtotal` (nights × rate × quantity) and its
    `cancellation` terms
  - `price`: `subtotal`, `discount` (`{code, amount}` or null), `taxes` (`[{name, included, amount}]`) and `total`
  - `booker`, pre-filled from the profile (the phone and country are null until saved)

  Lines are priced like the cart (the hotel page's room list; a group's lines of one room type share `roomsLeft`).
  Lines of more than one hotel or date range are 400, and a line not in the user's cart is 404. A line that can't be
  booked now is 409: check-in passed, the rate no longer sells the stay, or too few rooms are left.
- **Money** is in the hotel's currency, which is also the currency the booking is charged in. A total in the
  customer's display currency waits for exchange rates (C-SRCH-05).
  - **Taxes** are the active rules of the hotel's country (`tax_rules`) covering at least one night. Each rule
    covers only the nights inside its validity and is rounded half up once:
    - `PERCENT`: value % of the room price, or the share already inside it when `included`. With
      `applies_after_discount` (the default), the price is taken after the discount.
    - `PER_NIGHT`: value × rooms × nights. `PER_PERSON_NIGHT`: value × guests × nights. `PER_BOOKING`: value once.

    Included taxes are listed but not added: `total = subtotal − discount + the other taxes`. A fixed tax in a
    currency other than the hotel's makes checkout 409 until conversion exists.
  - **Discount codes** (one per booking) match in any case. A refused code is 400 with the reason:
    - not valid (unknown, inactive or deleted), not active yet, expired
    - another hotel, another currency, a minimum spend not met
    - used up, already used by this user, first booking only

    A percent code is capped by its `max_discount_minor`, and no code takes off more than the subtotal. Codes are
    written with SQL until the admin story (A-DISC-01); the demo seed has `welcome10`.
- **Booking** (`POST /api/bookings`, 201). Send the quote's `price.total` as `expectedTotal`, plus:
  - `booker {fullName, email, phone, countryCode}`, all required
  - `guestName` when booking for someone else
  - `specialRequests`: at most 1000 characters, passed to the hotel and not guaranteed
  - `estimatedArrival` (C-BOOK-06), e.g. `"18:30"`, in the hotel's local time
  - `leadGuests: [{cartItemId, names}]`: optional, at most one name per room
  - `acceptedTerms: true`, covering the terms, the privacy policy and the rates' cancellation policies

  Under the cart's row lock the lines are priced again (C-BOOK-04). If the total differs from `expectedTotal`, the
  answer is 409 "The total is now …" and nothing is written; the customer confirms by quoting again. Then the rooms
  are held: `room_inventory.booked_rooms` grows night by night, only where the rooms still fit, so two customers can
  never book the last room (the second gets 409). The booking is written `PENDING` with `holdExpiresAt` 15 minutes
  ahead (`app.booking.hold`). Each room booked is one row, with its guests, lead guest, nightly prices and a copy of
  its cancellation policy. Each tax is one row too. The code's use is recorded, and the lines leave the cart while its
  other groups stay. A second submit of the same lines is 404.
- **Hold expiry.** A booking not paid before its hold ends expires. `BookingHoldExpiryJob` runs every minute
  (`APP_BOOKING_EXPIRY_CRON`). It marks bookings past their hold `EXPIRED`, gives their rooms back and deletes their
  code use. A booking whose payment was started is skipped until Stripe has canceled that payment (see below).
- **Status rules live in the database** (V10's triggers). A booking starts `PENDING`, and only the lifecycle's
  arrows are allowed (Pending → Confirmed → Checked-in → Checked-out; Pending → Cancelled or Expired; Confirmed →
  Cancelled or No-show). Every change is logged in `booking_status_history`, with who and why.

```bash
# quote a cart group (cart line ids from GET /api/cart), then book it at the quoted total
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' $BASE/api/bookings/quote \
  -d '{"cartItemIds": [1, 2], "discountCode": "welcome10"}'
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' $BASE/api/bookings \
  -d '{"cartItemIds": [1, 2], "discountCode": "welcome10", "expectedTotal": {"amount": 3960000, "currency": "VND"},
       "booker": {"fullName": "Jane Doe", "email": "jane@example.com", "phone": "+84901234567", "countryCode": "VN"},
       "estimatedArrival": "18:30", "acceptedTerms": true}'
# 409 "The total is now ..." means the price changed: quote again and send the new total
```

## Payment and cancellation

A Pending booking is paid by card through Stripe (C-PAY-01/02). Card data and 3D Secure stay in Stripe's Payment
Element in the browser; the API only creates a PaymentIntent and listens to Stripe.

- **Start** (`POST /api/bookings/{reference}/payment`). It creates one PaymentIntent for the booking's total, in its
  currency, cards only. It answers the same intent, and so the same `clientSecret`, on every later call, also after a
  declined card. The client hands the secret and the publishable key to Stripe.js (`stripe.confirmPayment`).
  - A booking with nothing to pay (a 100% code) is confirmed at once, and `clientSecret` is null.
  - 409 when the booking isn't Pending, its hold ended, or its payment is already processing.
  - 503 when Stripe can't be reached or `APP_STRIPE_SECRET_KEY` is not set.
- **Confirmation comes only from Stripe** (`POST /api/payments/stripe/webhook`), never from the browser's redirect.
  - Every event is checked against `APP_STRIPE_WEBHOOK_SECRET` (400 otherwise) and logged once in `stripe_events`.
  - The PaymentIntent or refund it names is then read back from Stripe, so repeated or out-of-order events end in
    Stripe's current state.
  - On success, the Pending booking becomes `CONFIRMED` ("Paid by card" in its status log) and the emails go out.
  - A declined card leaves it Pending. `GET .../payment` then shows `FAILED` with Stripe's message, and the
    customer can retry on the same intent until `payBy`.
  - An event that fails to process answers 500 or 503, so Stripe sends it again; the error stays on the event row.
- **Hold end with a payment started.** `PaymentHoldExpiryJob` (on the expiry cron) cancels the PaymentIntent on
  Stripe, and only then does the booking expire and give its rooms back. A canceled intent can never charge. If
  Stripe answers that it already succeeded, the booking is confirmed instead. While Stripe can't be reached, the rooms
  stay held.
- **A payment that succeeds for a booking that is no longer Pending** (shouldn't happen) is recorded and logged as an
  ERROR. It is not confirmed or refunded automatically: refund it in the Stripe dashboard.
- **Booking page and emails** (C-PAY-03). `GET /api/bookings/{reference}` shows everything a confirmation lists:
  - the hotel's address and contact
  - rooms, guests, board, and each room's cancellation terms (from the policy it was sold with)
  - the breakdown, the payment, and the cancellation with its refund
  After the confirming transaction commits, the customer gets a confirmation and the hotel's partner contact
  (`partners.contact_email`) a new-booking notice. Emails are only logged for now.
- **Cancellation** (C-MYBK-03, the whole booking, Confirmed only).
  - `GET .../cancellation` previews `paid`, `penalty` and `refund` at this moment. A room's penalty is its
    policy's tier in effect, counted from the hotel's check-in time in its zone; a non-refundable rate costs the
    whole stay.
  - `refund = paid × (subtotal − penalty) ÷ subtotal`, rounded half up, so the discount and the taxes come back in
    proportion.
  - `POST .../cancel` takes the preview's refund as `expectedRefund` (409 with the new refund if a tier started in
    between). The booking becomes `CANCELLED` (logged as the customer), its rooms go back on sale, the code use stays
    counted, and the cancellation emails go out.
  - A Pending booking can't be cancelled (it expires).
- **Refunds** (C-PAY-05). The refund row is written with the cancellation and sent to Stripe after it commits, with
  the idempotency key `refund-{reference}`.
  - `RefundRetryJob` (`APP_REFUND_RETRY_CRON`) resends a refund that didn't reach Stripe. After 23 hours it marks it
    `FAILED` instead (Stripe keeps keys 24 hours, so a resend could refund twice).
  - `refund.*` webhooks keep the status (`PENDING`, `SUCCEEDED`, `FAILED`) on the booking.

**Stripe setup.**
1. Use test-mode keys from the Dashboard (Developers → API keys): `APP_STRIPE_SECRET_KEY=sk_test_...`. The
   publishable key goes to the frontend only.
2. Locally, `stripe listen --forward-to localhost:$SERVER_PORT/api/payments/stripe/webhook` prints the
   `whsec_...` secret for `APP_STRIPE_WEBHOOK_SECRET`.
3. In production, create a webhook endpoint for `/api/payments/stripe/webhook` with API version
   `2026-10-28.endive` (the one stripe-java 34.1.0 pins) and these events:
   - `payment_intent.succeeded`, `payment_intent.payment_failed`, `payment_intent.canceled`,
     `payment_intent.processing`
   - `refund.created`, `refund.updated`, `refund.failed`

```bash
# start paying a Pending booking: hand clientSecret to Stripe.js, then poll the payment
curl -s -X POST -H "Authorization: Bearer $TOKEN" $BASE/api/bookings/HB7K2M4QXA/payment
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/bookings/HB7K2M4QXA/payment
# cancel a paid booking at the previewed refund
curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/bookings/HB7K2M4QXA/cancellation
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  $BASE/api/bookings/HB7K2M4QXA/cancel -d '{"expectedRefund": {"amount": 3960000, "currency": "VND"}}'
```

## Known limitations (deliberate, for a base project)

- **Emails are not sent.** Verification and reset links are written to the log at INFO in every profile, so anyone
  who can read the log can reset any password. Replace `AuthMailServiceImpl` before launch.
- **Rate limits are kept in memory, per instance.** With N instances a client gets N times each limit, and a restart
  resets every count. Shared counts need a store all instances use (Redis, or a table in PostgreSQL).
- **Rate limits are per IP.** Many users behind one NAT (offices, mobile carriers) share one set of limits. An
  attacker with many IPs still gets each IP's full limits, so they can still lock known accounts for 15 minutes
  (only a CAPTCHA or growing delays would stop that).
- **Registration reveals whether an email exists** (409). Login, forgot password and resend do not, apart from
  response time (a known email does more work).
- **Two concurrent refreshes of one token sign that device out** (the second looks like a replay). Clients should
  send one refresh at a time.
- **One extra database lookup per authenticated request** (the session check that makes sign-out immediate).
- **Optimistic locking only guards concurrent transactions.** Clients don't send `version`, so a stale edit
  from a client can still overwrite newer data.
- **Hotel search has no currency conversion yet** (C-SRCH-05). Prices are in the hotel's own currency; the price
  filter, price sort and price range compare one currency (`currency`). There is no "recommended" sort (C-SRCH-03). Guests are split evenly over the rooms, so a hotel that could only host an uneven
  split (3 adults in a double + a single) is not shown.
- **Search prices are room prices only.** They don't change with the number of guests or children's ages, tax is a
  yes/no flag (the amounts are in the checkout quote), and discount codes apply only at checkout. Rates, policies and
  taxes have no write endpoints yet: enter them with SQL or the demo seed.
- **`night_price()` runs once per rate plan and night of every candidate hotel.** That's quick for a city. A
  country-wide search over thousands of hotels may need a precomputed price calendar. The filter counts price every
  listed hotel of the place (hotel filters can't prune them, since a hotel failing one filter still counts for that
  filter's options) and fill the rooms once per board, cancellation, bed and room-feature option. Measured only on
  the 8-hotel demo seed so far (about 0.1 s warm for both endpoints).
- **A hotel's `timezone` must be a valid IANA zone.** Search reads it to place the free-cancellation deadline (in Java
  for the cards, in the SQL function `free_cancellation_until()` for the `cancellation` filter), and an
  invalid value fails the request with a 500. The hotel write API (O-HOTEL-01) must validate it.
- **Public search endpoints still reject a bad token.** A request to `/api/search/**`, `/api/destinations/**` or
  `/api/hotels/**` with an expired or invalid `Authorization` header gets 401; clients should drop expired tokens. The
  public account endpoints (`/api/auth/login`, `/refresh`, `/logout`, …) ignore the header.
- **Reviews are read-only and the hotel ratings are stored, not computed.** Nothing writes reviews until customers
  can complete stays (C-MYBK-05), so `/reviews` is empty outside tests, and `user_rating_avg`/`user_rating_count`
  on hotels are not recomputed from reviews.
- **Hotel page terms that later stories may change.** A `FIXED` cancellation penalty is per room, like the prices, and
  a fractional `NIGHTS` penalty is charged pro rata; refunds (C-MYBK-03) will settle both. `fitsGuests` uses the
  search's even split, so a room type that would work in an uneven mix shows `false`. `roomsLeft` is the exact count;
  the client decides when to say "Only N left".
- **Cart prices have the search's limits.** They are per room in the hotel's currency, with a tax yes/no flag, and
  without conversion; the tax amounts and discount codes are in the checkout quote (C-SRCH-05).
- **Merged lines lose the browser's price notice.** A merged guest line starts at the merge's price, because the
  server doesn't trust a price the client sends.
- **A cart read prices each distinct stay and guests separately.** That is at most 20 room-list queries per read, and
  the cart's priced calls share the `search` rate limit.
- **Empty `carts` rows are kept.** The expiry job deletes lines, not the carts that held them.
- **Booking emails are only logged** (`BookingMailServiceImpl`: recipient and reference at INFO, the body at DEBUG).
  They are sent after the transaction commits, so a crash in between loses one; a real mailer needs an outbox
  (`email_outbox` in the schema doc) and a resend (C-MYBK-04). There is no booking list yet (C-MYBK-01).
- **Cards only.** Wallets and local methods are C-PAY-06. Stripe's special currencies are converted (ISK and UGX are
  sent ×100), and a three-decimal currency is refused unless the amount is in tens of its smallest unit.
- **Payments of expired holds are canceled 100 per minute.** While Stripe can't be reached, those bookings keep their
  rooms held, with an ERROR each minute.
- **No automatic refund of a misplaced payment.** A success Stripe reports for a booking that is no longer Pending, or
  for another amount, is recorded and logged as an ERROR, and someone refunds it in the Stripe dashboard (decided 15
  Nov 2026).
- **Failed refunds need a person.** A refund Stripe reports `FAILED`, or one that didn't reach Stripe within 23 hours,
  is logged as an ERROR; retrying it is the admin story A-PAY-01.
- **Cancellation is whole-booking and Confirmed-only.** One room of a booking can't be cancelled on its own, and a
  Pending booking expires instead of being cancelled. The penalty counts from the hotel's current check-in time and
  zone, so a later change to them moves the tiers of existing bookings. A cancelled paid booking keeps its code use.
- **`stripe_events` keeps every payload** (a failed attempt's billing details can be in it, never card numbers) with
  no retention job. The webhook shares the per-IP `general` rate limit; Stripe retries a 429.
- **Checkout money is in the hotel's currency only.** There is no total in the customer's display currency
  (C-BOOK-02 waits for C-SRCH-05). A fixed-amount tax or a discount code in another currency can't be used.
- **The commission amount isn't computed.** Bookings copy the partner's `commission_rate`; `commission_minor` stays 0
  until payouts (A-PAY-02) settle whether discounts reduce it ("Who funds discount codes" is an open question).
- **A hold creates the inventory rows it needs** from `room_types.total_rooms`. A later change to a room type's total
  doesn't reach those nights, as with any `room_inventory` row; the availability calendar (O-ROOM-03) edits them.
- **Discount codes and tax rules have no write endpoints** (A-DISC-01, A-CONF-02): enter them with SQL.
