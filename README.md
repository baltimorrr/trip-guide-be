# spring-base

A copyable starting point for REST APIs with Spring Boot and PostgreSQL. It has user registration and JWT login,
role-based access, consistent JSON errors, database migrations, API docs, and tests against a real database.
One sample feature (`product`) shows the conventions end to end.

It also holds the first features of the Hotel Booking Platform described in `USER_STORIES.md`: the homepage's
location autocomplete, hotel availability search and popular destinations (C-HOME-01/02/03), the priced hotel
result list (C-SRCH-01), plus the admin endpoints that curate those destinations. See [Search and destinations](#search-and-destinations). Customer accounts
(C-AUTH-01/02/04/05/06) have email verification, sign-in sessions per device, lockout, password reset and a profile.
See [Accounts and sessions](#accounts-and-sessions).

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
| GET | `/api/search/hotels?destination=\|hotel=&checkIn=&checkOut=&rooms=&adults=&childAges=` | public | paged; hotels that can sell the stay, each with its cheapest offer; 400 invalid criteria, 404 unknown slug |
| GET | `/api/destinations/popular?limit=&hotels=` | public | featured destinations with their best hotels; max 20 × 10 |
| GET | `/api/destinations/{slug}` | public | 404 if missing |
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

## Accounts and sessions

**Sign-up.** Passwords need 8–72 characters with a letter and a digit (and at most 72 bytes). A new account starts
unverified, and a verification link (24 h, works once) is sent. **No email service is wired yet: links are written
to the app log** (`LoggingAuthMailer`). Links point to the web app (`APP_FRONTEND_URL`): `/verify-email?token=…`
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
Hotels that can't are hidden, not shown as sold out. Results are ordered by user rating; `page.totalElements`
is the result count ("124 hotels in Đà Nẵng", the name coming from the suggestion or `/api/destinations/{slug}`).

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
curl -s "$BASE/api/destinations/popular"
curl -s -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Đà Nẵng","imageUrl":"https://example.com/da-nang.jpg","featuredRank":1}' \
  $BASE/api/admin/destinations/1
```

**Demo data.** Under the `local` profile Flyway also runs `db/seed/R__demo_catalog.sql`: 9 fictional hotels in Đà
Nẵng, Hồ Chí Minh, Hà Nội and Hội An (one inactive hotel in Huế), and those four cities featured. The `test` and
`prod` profiles never load it. `docker compose down -v` resets it.

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
| `SearchControllerPricingTest` | full app | cheapest plan and room type, night price order (override, rule priority, base), closed nights and minimum stays, unsellable room types, cheapest room mix, tax flag, free-cancellation deadline, photos, paging |
| `SearchCriteriaTest` | unit | date, length and guest rules; even split of guests per room |
| `FreeCancellationTest` | unit | deadline from the policy's penalty hours in the hotel's zone, non-refundable, passed deadline, daylight saving |
| `MoneyTest` | unit | minor units scaled by the currency's decimals |
| `DestinationServiceImplTest` | unit | featured order, fallback to top cities, limits |

Each test sits in the package of the class it covers (e.g. `product/controller/ProductControllerTest`).
Full-app tests use `@IntegrationTest` (`src/test/.../support`). Every class shares one Spring context and one
container, so the suite stays fast. Tests create their own data with unique emails and never assume an empty database.
`@IntegrationTest` swaps the logging mailer for `RecordingAuthMailer`, so tests follow emailed links like a user;
`TestAuth.userToken` registers, verifies and signs in.
Hotel-catalog tests build hotels with `CatalogFixtures` and unique place names, then call the destination sync
themselves (the `test` profile never schedules it). `CatalogFixtures.roomType` also adds a sellable rate plan
(`bareRoomType` adds none). Tax rules apply to a whole country, so tax tests use their own user-assigned country codes
(`XA`–`XZ`). Unit tests use Mockito and start no Spring context.

`TestProjectApplication` (in `src/test`) runs the app against a Testcontainers database instead of Docker Compose.

## Project structure

```text
src/main/java/com/company/project/
├── ProjectApplication.java
├── common/                              shared by every feature; imports no feature package
│   ├── config/                          SecurityConfig, OpenApiConfig (API docs), JpaAuditingConfig, SchedulingConfig
│   ├── persistence/                     DatabaseFunctionContributor: norm() callable from JPQL
│   ├── validation/                      @StrongPassword, @PhoneNumber, @IsoCountry, @IsoCurrency
│   ├── dto/                             PageResponse (paged list body: {content, page}), Money
│   ├── exception/                       GlobalExceptionHandler, BusinessException, ResourceNotFoundException,
│   │                                    ProblemDetailSecurityHandler (401/403 → problem+json)
│   └── model/BaseEntity.java            id, version, createdAt, updatedAt
├── auth/                                sign-up, verification, sign-in sessions, passwords, profile endpoints
│   ├── controller/  config/  dto/  model/  repository/ (+ impl/)  services/ (+ impl/)
│   ├── mail/                            AuthMailer; LoggingAuthMailer writes links to the log
│   └── security/                        JWT encode/decode, session check per request, opaque tokens, user lookup
├── user/                                User entity, roles, account creation, lockout counters, profile
│   ├── model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)
│   └── bootstrap/                       first admin account on startup
├── product/                             sample feature: copy this shape
│   └── controller/  model/  repository/  dto/  mapper/  services/ (+ impl/)
├── hotel/                               hotel catalog read model: entities, HotelRepository (priced availability),
│   └── model/  repository/ (+ impl/)    shared query fragments
├── rate/                                rate plans, board types, cancellation policies (read model)
│   └── model/  repository/ (+ impl/)
├── tax/                                 tax rules per country (read model)
│   └── model/  repository/ (+ impl/)
├── destination/                         destinations: sync, popular, lookup, admin curation
│   ├── controller/  model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)
│   └── sync/                            scheduled and on-startup destination sync
└── search/                              homepage search: suggestions, hotel result list with prices
    └── controller/  config/  model/  dto/  mapper/  services/ (+ impl/)
src/main/resources/
├── application.yml                      defaults for every profile
├── application-local.yml                local dev (the default profile): .env, Docker Compose, demo seed
├── application-prod.yml                 production overrides
├── db/migration/                        V1 users, V2 products, V3 hotel catalog + norm(), V4 tokens + sessions,
│                                        V5 rates + taxes + night_price()
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
   `@PreAuthorize("hasRole('ADMIN')")` where needed. Return `PageResponse.from(page)` for lists.
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

Access token lifetime is `app.jwt.access-token-ttl` (default `15m`) in `application.yml`. Account settings are
`app.auth.*` there: `refresh-token-ttl` 1d, `remember-me-ttl` 30d, `email-verification-ttl` 24h,
`password-reset-ttl` 30m, `email-cooldown` 1m, and `lockout` (`max-attempts` 5, `window` 15m, `duration` 15m). Search limits are `app.search.*`
there (`max-nights` 30, `max-days-ahead` 365, `max-rooms` 8, `max-adults` 30, `max-children` 10).

## Production notes

- Run with `SPRING_PROFILES_ACTIVE=prod`. This turns off Swagger UI and `/v3/api-docs`.
- Provide the database with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
  Docker Compose support is dev-only and is not included in the packaged jar.
- Build a jar with `./mvnw package` (`target/spring-base-0.0.1-SNAPSHOT.jar`), or a container image with
  `./mvnw spring-boot:build-image`.
- Provide `APP_JWT_SECRET` from a secret manager. Changing it invalidates every issued token.
- Set `APP_FRONTEND_URL` to the web app, and wire a real `AuthMailer` before real users sign up (see Known
  limitations).
- Behind a reverse proxy, set `server.forward-headers-strategy` so sessions record the client's IP, not the proxy's.
- Migration V3 creates the `unaccent` and `pg_trgm` extensions. Both are trusted extensions, so the database role
  needs CREATE on the database but not superuser.

## Using this as a template

1. Copy the folder **under a new folder name** and delete `target/`, `.env`, and `.git/`.
   Docker Compose names the database container and volume after the folder. Two copies both called
   `spring-base` would share one database.
2. Rename `groupId`/`artifactId`/`name` in `pom.xml`, the `com.company.project` package (and `ProjectApplication`
   if you like), and `spring.application.name`.
3. Delete or replace the `product` feature and `V2__create_product_tables.sql`, and the hotel features if you don't
   need them (`hotel/`, `rate/`, `tax/`, `destination/`, `search/`, `SchedulingConfig`,
   `V3__create_hotel_catalog_tables.sql`, `V5__create_rate_and_tax_tables.sql`, `db/seed/`, the `app.search` / `app.destinations` settings and their `SecurityConfig` matchers). If the copy is
   brand new and has never run, you may renumber migrations.
4. `git init`.

## Known limitations (deliberate, for a base project)

- **Emails are not sent.** Verification and reset links are written to the log at INFO in every profile, so anyone
  who can read the log can reset any password. Replace `LoggingAuthMailer` before launch.
- **No per-IP rate limiting.** Accounts lock after repeated failures, but nothing limits one client trying many
  emails, and anyone can lock a known email for 15 minutes. Add a limiter (e.g. Bucket4j or at the gateway).
- **Registration reveals whether an email exists** (409). Login, forgot password and resend do not, apart from
  response time (a known email does more work).
- **Two concurrent refreshes of one token sign that device out** (the second looks like a replay). Clients should
  send one refresh at a time.
- **One extra database lookup per authenticated request** (the session check that makes sign-out immediate).
- **Optimistic locking only guards concurrent transactions.** Clients don't send `version`, so a stale edit
  from a client can still overwrite newer data.
- **Hotel search has no sorting, filters or currency conversion yet** (C-SRCH-02/03/05). `sort` is ignored, and prices
  are in the hotel's own currency. Guests are split evenly over the rooms, so a hotel that could only host an uneven
  split (3 adults in a double + a single) is not shown.
- **Search prices are room prices only.** They don't change with the number of guests or children's ages, tax is a
  yes/no flag (amounts come with checkout, C-BOOK-02), and there are no discounts or promotions. Rates, policies and
  taxes have no write endpoints yet: enter them with SQL or the demo seed.
- **`night_price()` runs once per rate plan and night of every candidate hotel.** That's quick for a city. A
  country-wide search over thousands of hotels may need a precomputed price calendar.
- **A hotel's `timezone` must be a valid IANA zone.** Search reads it to place the free-cancellation deadline, and an
  invalid value fails the request with a 500. The hotel write API (O-HOTEL-01) must validate it.
- **Public search endpoints still reject a bad token.** A request to `/api/search/**` or `/api/destinations/**` with
  an expired or invalid `Authorization` header gets 401; clients should drop expired tokens. The public account
  endpoints (`/api/auth/login`, `/refresh`, `/logout`, …) ignore the header.
