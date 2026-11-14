# spring-base

A copyable starting point for REST APIs with Spring Boot and PostgreSQL. It has user registration and JWT login,
role-based access, consistent JSON errors, database migrations, API docs, and tests against a real database.
One sample feature (`product`) shows the conventions end to end.

It also holds the first features of the Hotel Booking Platform described in `USER_STORIES.md`: the homepage's
location autocomplete, hotel availability search and popular destinations (C-HOME-01/02/03), the priced hotel
result list (C-SRCH-01) with its filters, filter counts and sorting (C-SRCH-02/03), plus the admin endpoints that
curate those destinations. See [Search and destinations](#search-and-destinations). Customer accounts
(C-AUTH-01/02/04/05/06) have email verification, sign-in sessions per device, lockout, password reset and a profile.
See [Accounts and sessions](#accounts-and-sessions). Signed-in customers can save hotels to a wishlist (C-SRCH-08).
See [Wishlist](#wishlist). Each hotel has a public page: overview, facilities and policies, the rooms and rates for a
stay with their cancellation penalties, and reviews (C-HOTEL-01/02/03/04/06/07). See
[Hotel detail page](#hotel-detail-page). Signed-in customers keep a cart of rooms from any hotels and dates, priced
again on every read and grouped into future bookings, and a guest's browser cart merges into it on sign-in
(C-CART-01/02/03/04). See [Cart](#cart).

## Stack

| Piece | Version |
|-------|---------|
| Java | 21 |
| Spring Boot | 4.1.1 (Spring Framework 7, Spring Security 7.1, Hibernate 7) |
| PostgreSQL | 18 (Docker Compose locally, Testcontainers in tests) |
| Flyway | 12 (SQL migrations) |
| springdoc-openapi | 3.1.1 (Swagger UI) |
| Lombok | managed by Boot |
| Build | Maven via the `./mvnw` wrapper (no Maven install needed) |

## Prerequisites

- **JDK 21**: a full JDK, not just a runtime. `javac -version` must work.
  Ubuntu/Debian: `sudo apt install openjdk-21-jdk-headless`.
- **Docker** with Compose v2, running. The app starts its own Postgres container, and the tests use Testcontainers.
- IDE: enable annotation processing (Lombok). Set the run configuration's working directory to this folder so `.env` is found.

## Quick start

```bash
cp .env.example .env
openssl rand -base64 32          # paste the output into APP_JWT_SECRET in .env
# optional: set APP_BOOTSTRAP_ADMIN_PASSWORD (12+ chars) to get an admin account on first start
./mvnw spring-boot:run           # run from this folder
```

On startup Spring Boot runs `docker compose up` for Postgres, waits until it is healthy, applies the Flyway
migrations, and listens on port 8080.

- Health: <http://localhost:8080/actuator/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>. Click **Authorize** and paste an access token.

**Port already in use?** Set `SERVER_PORT` (app) or `POSTGRES_PORT` (database) in `.env`.

The database keeps running after the app stops, so restarts are fast. Stop it with `docker compose stop`.
`docker compose down -v` deletes it with all its data.

The app refuses to start if `APP_JWT_SECRET` is missing or shorter than 32 bytes, and the error says how to fix it.

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

A request under a specific limit also counts toward `general`. Counts come back gradually, not all at once: after 10
sign-ins, one more is allowed every 6 seconds. Over a limit, the API answers `429` problem+json
`"Too many requests, retry later"` with a `Retry-After` header (seconds). The check runs before the token, the body
and the password are checked, and after CORS, so browser code can read the 429.

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
  will book as one booking (C-BOOK-01, not built yet). Groups come by check-in, then hotel name. Lines come in the
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

## Tests

```bash
./mvnw verify      # needs Docker running; starts a throwaway PostgreSQL 18 container
```

| Test | Kind | Covers |
|------|------|--------|
| `ProjectApplicationTest` | full app | context starts, health is UP |
| `ErrorResponseTest` | full app | problem+json for unknown paths, API docs served |
| `AuthControllerTest` | full app | register (password rule, confirmation, 72-byte limit), verification link and resend, unverified sign-in, admin bootstrap |
| `AuthControllerSessionTest` | full app | token pair, stored hash and device, remember me, refresh rotation, reuse revokes, sign-out one/all devices, disabled user, stale header ignored |
| `AuthControllerLockoutTest` | full app | lock after 5 failures, same 401, lock expiry, window restart, success resets |
| `AuthControllerPasswordTest` | full app | forgot (no enumeration), reset once/expired/replaced, signs out everywhere, unlocks, verifies |
| `ProfileControllerTest` | full app | me, bad/tampered tokens, profile update and validation, change password keeps this device only |
| `UserSessionRepositoryTest` | JPA slice | revoke all / all but one, lookup by current or previous hash, live-session check |
| `SessionServiceImplTest`, `SessionTokenValidatorTest` | unit | rotation, reuse and ended sessions → 401; `sid` check |
| `UserTokenServiceImplTest`, `PasswordServiceImplTest`, `ProfileServiceImplTest` | unit | link cooldown/replacement/use, reset and change rules, profile normalization |
| `OpaqueTokensTest` | unit | random url-safe tokens, SHA-256 hashes |
| `ProductControllerTest` | full app | CRUD, USER vs ADMIN (403), 404, validation, sort/page limits, write-response timestamps equal stored ones |
| `UserRepositoryTest` | JPA slice | mapping, roles table, unique email, audit timestamps, lockout counting |
| `TokenServiceTest` | unit | claims incl. `sid`, wrong key, expired (no session lookup), wrong issuer, inactive session, weak secret |
| `UserServiceImplTest` | unit | 72-byte password limit, duplicate email (409), normalized email and hashed password, verified flag, change password |
| `ProductServiceImplTest` | unit | 404 for a missing id, create/update go through the mapper |
| `AdminBootstrapTest` | unit | a concurrently created admin is skipped, any other error still fails startup, admin is verified |
| `DestinationRepositoryTest` | full app | `norm()`, generated normalized name and keys, place-key uniqueness and CHECKs |
| `DestinationSyncServiceImplTest` | full app | places from listed hotels only, every level, slugs, counts, spellings chosen and refreshed, admin fields kept, country names |
| `SearchControllerTest` | full app | accent-insensitive suggestions, half-typed words and typos in place names, escaped wildcards, availability (closed/sold-out nights, capacity, several rooms, children), validation |
| `DestinationControllerTest` | full app | popular order and best hotels, default search for a clicked destination, lookup by slug |
| `AdminDestinationControllerTest` | full app | ADMIN only, name-prefix list, update + validation, renamed country in its places' labels, sync on demand |
| `SearchControllerFilterTest` | full app | each hotel filter, name matching and escaping, board/cancellation/bed/feature filters choosing the offer, price per night and currency, validation |
| `SearchControllerSortTest` | full app | price (other currencies last), rating with the review minimum, distance, paging in sort order, unknown sorts |
| `SearchControllerFilterCountsTest` | full app | "any of" vs "all of" counts, thresholds, re-priced offer options, every count equal to the list's total with that option, price range, option names, empty place |
| `SearchControllerPricingTest` | full app | cheapest plan and room type, night price order (override, rule priority, base), closed nights and minimum stays, unsellable room types, cheapest room mix, tax flag, free-cancellation deadline, photos, paging |
| `SearchCriteriaTest` | unit | date, length and guest rules; even split of guests per room |
| `FreeCancellationTest` | unit | deadline from the policy's penalty hours in the hotel's zone, non-refundable, passed deadline, daylight saving |
| `FreeCancellationSqlTest` | full app | the SQL function `free_cancellation_until()` gives the same deadline as `FreeCancellation` |
| `HotelSortTest` | unit | reading `sort`: any case, direction ignored, unknown or several values rejected |
| `MoneyTest` | unit | minor units scaled by the currency's decimals, and back |
| `DestinationServiceImplTest` | unit | featured order, fallback to top cities, limits |
| `WishlistControllerTest` | full app | saving twice keeps one entry and its first `savedAt`, newest first and paging, idempotent remove, only listed hotels can be saved, delisted hotels stay as unavailable, one list per user, sign-in required |
| `WishlistServiceImplTest` | unit | 404 without an insert, list keeps the entry order and availability, empty page skips the card query |
| `HotelControllerTest` | full app | overview fields, formatted address with the country's name or code, gallery order and categories, grouped and paid facilities, times and policies, only listed hotels |
| `HotelControllerRoomsTest` | full app | every sellable rate (inactive, other-currency, closed and too-long-minimum-stay plans left out), rooms left, sold-out and withdrawn room types hidden, unfit room types last and marked, even split, room details, night prices and average, tax flag, cheapest fitting rate equal to the search card, penalty timelines, invalid stays |
| `HotelMapperTest` | unit | address formatting, facility group order |
| `HotelServiceImplTest` | unit | nothing sellable skips the room and rate queries, 404 and 400 before pricing |
| `CancellationScheduleTest` | unit | tiers from the rules' hours, first tier at `FreeCancellation`'s deadline, non-refundable, passed tiers, nights, fixed and percent amounts capped at the stay, free rules, daylight saving |
| `ReviewControllerTest` | full app | newest first with every field, score and date sorts with ties newest first, hidden and other hotels' reviews never listed or counted, unsupported sort, only listed hotels |
| `ReviewRepositoryTest` | full app | one review per booking, score range, enum CHECKs, published reviews of one hotel |
| `ReviewServiceImplTest` | unit | default and tie-break order, unsupported sort and unknown hotel before any review query |
| `RateLimitFilterTest` | full app | each limit enforced with 429 problem+json and `Retry-After`, before the body is read, per address, shared buckets, 401s count, health never limited |
| `RateLimiterTest` | unit | capacity, gradual refill, rounded-up wait, separate buckets per client and limit, IPv6 /64 and IPv4-mapped client keys |
| `RateLimitPolicyTest` | unit | the cart's priced calls spend from `search`, removing a line doesn't, search endpoints keep it |
| `CartControllerTest` | full app | every line and group field, the same line adds up (ages in any order), groups by hotel and dates, 404/400/409 checks leaving the cart unchanged, rooms shared by a group's lines of one room type, full cart, one cart per user, sign-in required |
| `CartControllerPricesTest` | full app | changed price with old and new, not enough rooms, unavailable (closed night, inactive plan, unlisted hotel), passed check-in, same price and terms as the hotel page, group total of available lines only |
| `CartControllerEditTest` | full app | quantity edit keeps the notice, new dates move the line at today's price, dates onto another line merge them, rejected edits change nothing, other users' lines, idempotent remove |
| `CartControllerMergeTest` | full app | browser items added, larger quantity kept, second merge changes nothing, skipped items with index and reason, merged lines share the rooms check, item limit |
| `CartItemRepositoryTest` | full app | `child_ages` mapping and sorting, one line per rate/stay/guests, the expiry delete, the cart row lock |
| `CartServiceImplTest` | unit | empty cart reads only its lines, passed lines aren't priced, one room-list call per stay and guests, not-enough-rooms per group and room type |
| `CartExpiryTest` | full app | passed lines reported, then deleted; today's check-in kept |

Each test sits in the package of the class it covers (e.g. `product/controller/ProductControllerTest`).
Full-app tests use `@IntegrationTest` (`src/test/.../support`). Every class shares one Spring context and one
container, so the suite stays fast. Tests create their own data with unique emails and never assume an empty database.
`@IntegrationTest` swaps the logging mail service for `RecordingAuthMailService`, so tests follow emailed links like a user;
`TestAuth.userToken` registers, verifies and signs in.
Hotel-catalog tests build hotels with `CatalogFixtures` and unique place names, then call the destination sync
themselves (the `test` profile never schedules it). `CatalogFixtures.roomType` also adds a sellable rate plan
(`bareRoomType` adds none). `hotelAmenity`, `roomAmenity` and `bed` attach V6 reference rows. Tax rules apply to a whole country, so tax tests use their own user-assigned country codes
(`XA`–`XZ`). `review` inserts reviews with a made-up `booking_id` (no bookings table yet), written by a `user()` that
can't sign in. `defaultRatePlan` returns the plan `roomType` added, `cartItem` writes cart lines the API would
refuse (check-ins already past), and `userId` reads an account's id; cart controller tests sign up through their
`CartClient`. The `test` profile exempts MockMvc's `127.0.0.1` from rate limits, so `RateLimitFilterTest` sends
from its own `203.0.113.x` addresses. Unit tests use Mockito and start no Spring context.

`TestProjectApplication` (in `src/test`) runs the app against a Testcontainers database instead of Docker Compose.

## Project structure

```text
src/main/java/com/company/project/
├── ProjectApplication.java
├── common/                              shared by every feature; imports no feature package
│   ├── config/                          SecurityConfig, OpenApiConfig (API docs), JpaAuditingConfig, SchedulingConfig
│   ├── persistence/                     DatabaseFunctionContributor: norm() callable from JPQL
│   ├── ratelimit/                       RateLimitFilter, RateLimitPolicy (which requests), RateLimiter (buckets)
│   ├── validation/                      @StrongPassword, @PhoneNumber, @IsoCountry, @IsoCurrency
│   ├── dto/                             PageResponse (paged list body: {content, page}), Money
│   ├── exception/                       GlobalExceptionHandler, BusinessException, ResourceNotFoundException,
│   │                                    RateLimitExceededException (429),
│   │                                    ProblemDetailSecurityHandler (401/403 → problem+json)
│   └── model/BaseEntity.java            id, version, createdAt, updatedAt
├── auth/                                sign-up, verification, sign-in sessions, passwords, profile endpoints
│   ├── controller/  config/  dto/  model/  repository/ (+ impl/)  services/ (+ impl/)
│   └── security/                        JWT encode/decode, session check per request, opaque tokens, user lookup
├── user/                                User entity, roles, account creation, lockout counters, profile
│   ├── model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)
│   └── bootstrap/                       first admin account on startup
├── product/                             sample feature: copy this shape
│   └── controller/  model/  repository/  dto/  mapper/  services/ (+ impl/)
├── hotel/                               hotel catalog: entities, amenities, beds, policies, HotelRepository (priced
│   │                                    availability, filters, filter counts, room list), shared query fragments
│   └── controller/  model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)   hotel page endpoints
├── rate/                                rate plans, board types, cancellation policies (read model)
│   └── model/  repository/ (+ impl/)
├── tax/                                 tax rules per country (read model)
│   └── model/  repository/ (+ impl/)
├── destination/                         destinations: sync, popular, lookup, admin curation
│   ├── controller/  model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)
│   └── sync/                            scheduled and on-startup destination sync
├── search/                              homepage search: suggestions, result list with prices, filters, sorting;
│   │                                    stay rules and cancellation terms the hotel page shares
│   └── controller/  config/  model/  dto/  mapper/  services/ (+ impl/)
├── wishlist/                            each user's saved hotels
│   └── controller/  model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)
├── cart/                                each user's cart, priced live by the hotel page's room list
│   ├── controller/  config/  model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)
│   └── expiry/                          scheduled delete of lines whose check-in passed
└── review/                              hotel reviews, read on the hotel page
    └── controller/  model/  repository/  dto/  mapper/  services/ (+ impl/)
src/main/resources/
├── application.yml                      defaults for every profile
├── application-local.yml                local dev (the default profile): .env, Docker Compose, demo seed
├── application-prod.yml                 production overrides
├── db/migration/                        V1 users, V2 products, V3 hotel catalog + norm(), V4 tokens + sessions,
│                                        V5 rates + taxes + night_price(), V6 amenities + beds +
│                                        free_cancellation_until(), V7 wishlists, V8 hotel policies + reviews,
│                                        V9 carts
└── db/seed/R__demo_catalog.sql          demo hotels, local profile only
```

Code is grouped **by feature, then by layer**:

- `controller/`: REST endpoints. Depends on the service interface, never on `impl/`.
- `services/`: the service interface (the contract). `services/impl/` holds the `@Service` implementation and its
  transactions.
- `repository/`: follows the services pattern.
  - `ThingRepository` extends `JpaRepository` and declares only what Spring Data generates (CRUD, derived queries
    like `findBySlugAndDeletedAtIsNull`).
  - Hand-written queries are declared in `ThingRepositoryCustom`, which `ThingRepository` also extends.
  - `repository/impl/ThingRepositoryImpl` implements them with `EntityManager`. Spring Data finds it by the
    `Impl` suffix, so services still inject just `ThingRepository`.
  - In the impl: JPQL first, native SQL only when JPQL can't express the query (the per-night availability check,
    the sync's bulk upsert).
  - A repository with nothing Spring Data can generate (`DestinationSyncRepository`) is a plain interface plus a
    `@Repository` impl.
  - Query result records (`...View`) live in `repository/`.
- `model/`: JPA entities, enums and value objects.
- `config/`: a feature's own `@ConfigurationProperties` record.
- `dto/`: request and response records. `mapper/`: `@Component` classes that convert entities and projections to
  DTOs.

A folder only exists when a feature has something in it. For example, `user/` has no controller because its
endpoints live in `auth/`.

## Adding a feature (checklist)

1. **Migration**: add `src/main/resources/db/migration/V{next}__create_thing_tables.sql` with the standard columns
   (`id bigint generated by default as identity primary key`, `version bigint not null default 0`,
   `created_at timestamptz not null`, `updated_at timestamptz not null`).
2. **Entity**: `thing/model/Thing.java extends BaseEntity`, with `@Getter` and `@NoArgsConstructor(access = PROTECTED)`.
3. **Repository**: `thing/repository/ThingRepository extends JpaRepository<Thing, Long>` with derived queries. For
   hand-written queries, add `ThingRepositoryCustom` (which `ThingRepository` extends) and implement it in
   `thing/repository/impl/ThingRepositoryImpl` with `EntityManager`: JPQL first, native SQL only when needed.
4. **DTOs**: `thing/dto/`, request and response `record`s with Bean Validation annotations.
5. **Mapper**: `thing/mapper/ThingMapper` (`@Component`) with `toEntity`, `updateEntity`, and `toResponse`.
6. **Service**: interface `thing/services/ThingService`; implementation `thing/services/impl/ThingServiceImpl` with
   `@Transactional(readOnly = true)` on the class and `@Transactional` on writes. Throw `ResourceNotFoundException`
   or `BusinessException(HttpStatus.CONFLICT, ...)` (any status).
7. **Controller**: `thing/controller/ThingController`, `/api/things`, injecting `ThingService`, `@Valid @RequestBody`,
   `@PreAuthorize("hasRole('ADMIN')")` where needed. Return `PageResponse.from(page)` for lists. An endpoint that
   is expensive (heavy SQL) or abusable (sends email, checks a secret) also gets a matcher in `RateLimitPolicy`.
8. **Tests**: `controller/ThingControllerTest` (`@IntegrationTest`, `TestAuth` for tokens) and
   `services/impl/ThingServiceImplTest` (Mockito).

## Conventions

- **Never edit a migration that has run anywhere.** Add a new one. Hibernate only validates the schema (`ddl-auto: validate`).
- No Lombok `@Data`, `@ToString`, or `@EqualsAndHashCode` on entities. `BaseEntity` defines id-based equality.
- `createdAt`/`updatedAt` come from Spring Data auditing (`JpaAuditingConfig`). A `@DataJpaTest` must
  `@Import(JpaAuditingConfig.class)`, or inserts fail on the not-null timestamp columns.
- DTOs are records, converted by the feature's mapper. Entities never leave the service layer.
- Requests carrying secrets override `toString()` so passwords cannot end up in logs.
- Errors are thrown as `BusinessException(HttpStatus, message)` (`ResourceNotFoundException` for 404) and rendered
  with that status by `GlobalExceptionHandler`. Controllers don't build error responses.

## Configuration

All settings can come from environment variables. Under the `local` profile they are also read from `.env`.

Profiles: `local` is the default when none is set (reads `.env`, starts Docker Compose), `prod` is for production
(see below), and `test` is activated by the tests.

| Variable | Default | Purpose |
|----------|---------|---------|
| `APP_JWT_SECRET` | required | base64 of ≥ 32 random bytes (`openssl rand -base64 32`) |
| `APP_BOOTSTRAP_ADMIN_EMAIL` / `APP_BOOTSTRAP_ADMIN_PASSWORD` | unset | creates this ADMIN on startup if it doesn't exist (password ≥ 12 chars) |
| `APP_CORS_ALLOWED_ORIGINS` | empty (no browser origins) | comma-separated, e.g. `http://localhost:5173` |
| `APP_FRONTEND_URL` | `http://localhost:5173` | web app that verification and reset links open |
| `SERVER_PORT` | 8080 | HTTP port |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_PORT` | app / app / required / 5432 | local Compose database |
| `APP_DESTINATIONS_SYNC_CRON` | `0 */10 * * * *` | destination sync schedule (Spring cron, six fields); `-` disables it |
| `APP_DESTINATIONS_SYNC_ON_STARTUP` | `true` | run the destination sync once when the app is ready |
| `APP_CART_EXPIRY_CRON` | `0 0 * * * *` | when to delete cart lines whose check-in passed (Spring cron, six fields); `-` disables it |
| `APP_RATE_LIMIT_ENABLED` | `true` | `false` turns every rate limit off, e.g. when a gateway limits instead |
| `APP_RATE_LIMIT_EXEMPT_ADDRESSES` | empty | comma-separated client addresses never limited, e.g. an internal monitor; `127.0.0.1` in a local `.env` keeps you from limiting yourself |

Access token lifetime is `app.jwt.access-token-ttl` (default `15m`) in `application.yml`. Account settings are
`app.auth.*` there: `refresh-token-ttl` 1d, `remember-me-ttl` 30d, `email-verification-ttl` 24h,
`password-reset-ttl` 30m, `email-cooldown` 1m, and `lockout` (`max-attempts` 5, `window` 15m, `duration` 15m). Search limits are `app.search.*`
there (`max-nights` 30, `max-days-ahead` 365, `max-rooms` 8, `max-adults` 30, `max-children` 10,
`rating-sort-min-reviews` 10); cart quantities and guests follow them, and `app.cart.max-items` (20) caps the lines
of one cart. Rate limits are `app.rate-limit.limits.*` there (`capacity` per `period`, values in the
table under Endpoints), and `max-tracked-clients` (50000) caps how many clients each limit remembers.

## Production notes

- Run with `SPRING_PROFILES_ACTIVE=prod`. This turns off Swagger UI and `/v3/api-docs`.
- Provide the database with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
  Docker Compose support is dev-only and is not included in the packaged jar.
- Build a jar with `./mvnw package` (`target/spring-base-0.0.1-SNAPSHOT.jar`), or a container image with
  `./mvnw spring-boot:build-image`.
- Provide `APP_JWT_SECRET` from a secret manager. Changing it invalidates every issued token.
- Set `APP_FRONTEND_URL` to the web app, and wire a real `AuthMailService` before real users sign up (see Known
  limitations).
- Behind a reverse proxy, set `server.forward-headers-strategy=native`. Without it, every request seems to come from
  the proxy: all clients share one set of rate limits (the whole site gets 429s), and sessions record the proxy's IP.
  `native` trusts `X-Forwarded-For` only from proxies with private addresses (Tomcat's `internal-proxies`). Set
  `server.tomcat.remoteip.internal-proxies` if yours has a public one. Never let clients reach the app directly while
  forwarded headers are trusted, or they can pick their own IP.
- Migration V3 creates the `unaccent` and `pg_trgm` extensions. Both are trusted extensions, so the database role
  needs CREATE on the database but not superuser.

## Using this as a template

1. Copy the folder **under a new folder name** and delete `target/`, `.env`, and `.git/`.
   Docker Compose names the database container and volume after the folder. Two copies both called
   `spring-base` would share one database.
2. Rename `groupId`/`artifactId`/`name` in `pom.xml`, the `com.company.project` package (and `ProjectApplication`
   if you like), and `spring.application.name`.
3. Delete or replace the `product` feature and `V2__create_product_tables.sql`, and the hotel features if you don't
   need them (`hotel/`, `rate/`, `tax/`, `destination/`, `search/`, `wishlist/`, `review/`, `cart/`,
   `SchedulingConfig`, `V3__create_hotel_catalog_tables.sql`, `V5__create_rate_and_tax_tables.sql`,
   `V6__create_amenity_and_bed_tables.sql`, `V7__create_wishlist_table.sql`,
   `V8__create_hotel_policy_and_review_tables.sql`, `V9__create_cart_tables.sql`, `db/seed/`, the `app.search` /
   `app.destinations` / `app.cart` settings, their `SecurityConfig` matchers and the cart paths in
   `RateLimitPolicy`). If the copy is
   brand new and has never run, you may renumber migrations.
4. `git init`.

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
  yes/no flag (amounts come with checkout, C-BOOK-02), and there are no discounts or promotions. Rates, policies and
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
  can complete stays (C-MYBK-05), so `/reviews` is empty outside tests. `reviews.booking_id` has no foreign key until
  the `bookings` table exists, and `user_rating_avg`/`user_rating_count` on hotels are not recomputed from reviews.
- **Hotel page terms that later stories may change.** A `FIXED` cancellation penalty is per room, like the prices, and
  a fractional `NIGHTS` penalty is charged pro rata; refunds (C-MYBK-03) will settle both. `fitsGuests` uses the
  search's even split, so a room type that would work in an uneven mix shows `false`. `roomsLeft` is the exact count;
  the client decides when to say "Only N left".
- **The cart can't be checked out yet, and holds nothing.** Checkout of a group, and rejecting one that mixes hotels
  or dates, come with bookings (C-BOOK-01), and rooms are held only once checkout starts (C-BOOK-04).
- **Cart prices have the search's limits.** They are per room in the hotel's currency, with a tax yes/no flag, and
  without conversion, tax amounts or discounts (C-SRCH-05, C-BOOK-02/03).
- **Merged lines lose the browser's price notice.** A merged guest line starts at the merge's price, because the
  server doesn't trust a price the client sends.
- **A cart read prices each distinct stay and guests separately.** That is at most 20 room-list queries per read, and
  the cart's priced calls share the `search` rate limit.
- **Empty `carts` rows are kept.** The expiry job deletes lines, not the carts that held them.
