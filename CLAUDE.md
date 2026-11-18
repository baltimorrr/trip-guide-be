# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A REST API base project: Java 21, Spring Boot 4.1 (Spring Framework 7, Security 7.1, Hibernate 7), PostgreSQL 18,
Flyway, springdoc, Lombok, Testcontainers. It has JWT auth, role-based access, and RFC 9457 errors. `product` is a
sample feature that shows every convention end to end. `README.md` has the endpoint table, env var table, production
notes and the "Adding a feature" checklist.

`USER_STORIES.md` and `DATABASE-SCHEMA.md` (dbdiagram.io DBML, 54 tables) describe the Hotel Booking Platform this base
is meant to become. Implemented so far: the homepage stories C-HOME-01/02/03 (`search/`, `destination/`, migration V3),
the priced result list C-SRCH-01 (`rate/`, `tax/`, migration V5), its filters, filter counts and sorts C-SRCH-02/03
(migration V6: amenities, bed types, `free_cancellation_until()`; the "recommended" sort is postponed), the admin
slice of A-CONT-01 that curates destinations, the email-account stories C-AUTH-01/02/04/05/06 (`auth/`, `user/`,
migration V4; emails are only logged), the wishlist C-SRCH-08 (`wishlist/`, migration V7; search results don't
mark saved hotels, clients draw the hearts from `GET /api/wishlist`), and the hotel page C-HOTEL-01/02/03/04/06/07
(`hotel/` controller and service, `review/`, migration V8: `hotel_policies`, `reviews`; reviews are read-only until
C-MYBK-05), the cart C-CART-01/02/03/04 (`cart/`, migration V9), checkout C-BOOK-01..06 (`booking/`,
`discount/`, migration V10: a quote, then a Pending booking that holds its rooms), and card payment, confirmation,
cancellation and refunds C-PAY-01/02/03/05 + C-MYBK-03 (`payment/`, `booking/`, migration V11: Stripe
PaymentIntents confirmed by the webhook, the booking page and logged emails, whole-booking cancellation with a
proportional refund). The schema doc says it was generated from a `schema.sql` that is not in this repo. **Decided
(2026-10-28), hotel page:** `reviews` follows the doc exactly (`booking_id` not null and unique; its FK is added by
the migration that creates `bookings`, V10), so the demo seed has no reviews; room types that don't fit the guests are
listed last with `fitsGuests: false`, not hidden.

**Decided (2026-10-21): new tables keep the schema doc's table and column names but follow this project's
conventions.** That means identity `bigint` ids, a `version` column, and `created_at`/`updated_at` set by auditing.
Enum-like columns are `varchar` + CHECK holding the Java enum names (UPPERCASE). `char(n)` becomes `varchar(n)` + a
regex CHECK, and `citext` becomes `varchar` stored lowercased. Tables with a composite natural key in the doc
(`room_inventory`) keep it without id/version, like `user_roles`. A foreign key to a table that doesn't exist yet
(e.g. `hotels.currency` → `currencies`) is added by the migration that creates that table. **Decided (2026-10-22):**
`destinations` also stores spellings (`state_province`, `city`, `district`, `ward`), and its `*_key` columns
are generated from them. The doc's `destinations` table was edited by hand to match,
so it no longer matches that `schema.sql`. **Decided (2026-10-22), sessions:** one `user_sessions` row per sign-in;
the refresh token travels in the JSON body and rotates on every refresh; replaying the previous token revokes the
session (`previous_token_hash`); access tokens carry `sid` and are checked against the session on every request
(immediate revocation). `users`/`user_sessions` gained columns the doc lacked (`first_failed_login_at`,
`previous_token_hash`, `last_used_at`; `ip` is varchar), and the doc was edited to match. **Decided (2026-10-30),
rate limiting:** every request is limited per client IP (IPv6 by its /64), with stricter limits on sign-in,
registration, the email-sending endpoints and the priced search. Counts live in memory per instance (Bucket4j +
Caffeine), not in a shared store. **Decided (2026-11-13), cart:** carts are server-side for signed-in users only
(`carts.user_id` not null and unique, no `guest_token`; the doc was edited to match). Guests keep theirs in the
browser and send it to `POST /api/cart/merge` after signing in. On merge a line already in the cart keeps the larger
quantity, while adding a line again (`POST /api/cart/items`) adds the quantities up. **Decided (2026-11-14), bookings:** an
account is required (checkout starts from the server cart, `bookings.user_id` not null); a booking is charged in the
hotel's currency, and the display-currency total waits for exchange rates (C-SRCH-05); the booked lines leave the
cart when the Pending booking is created; the rooms are held from that moment (the guest form is submitted), not
from the quote, because every booking row needs the booker and accepted terms. **Decided (2026-11-15), payments:**
Stripe PaymentIntent + Payment Element (not Checkout Sessions, whose 30-minute minimum can't match the 15-minute
hold); cards only; a success reported for a booking that is no longer Pending is recorded and logged as ERROR for a
manual refund, never auto-refunded; C-MYBK-03 cancels the whole booking, Confirmed only (a Pending one expires), and
the refund is `paid × (subtotal − penalty) ÷ subtotal`, rounded half up once. Still open: roles
(`customer/hotel_owner/hotel_staff/admin` vs code's `USER/ADMIN`). Ask before changing it.

## Commands

```bash
cp .env.example .env    # then set APP_JWT_SECRET=$(openssl rand -base64 32); the app will not start without it
./mvnw spring-boot:run  # profile "local": reads .env, runs `docker compose up` for Postgres, port 8080
./mvnw verify           # full suite; Docker must be running (Testcontainers)
./mvnw test -Dtest=TokenServiceTest                                    # one class
./mvnw test -Dtest='ProductControllerTest#userCanListAndReadProducts'  # one method
./mvnw package          # target/spring-base-0.0.1-SNAPSHOT.jar
docker compose stop     # the app leaves Postgres running; `docker compose down -v` deletes the data
docker compose --profile app up --build  # app + Postgres in Docker (Dockerfile), no JDK needed
```

- Mockito unit tests run without Docker. Any `@IntegrationTest` or `@DataJpaTest` class needs Docker.
- `TestProjectApplication` (in `src/test`) runs the app against a Testcontainers Postgres instead of Compose.
- No linter or formatter is configured. Code uses tabs and Spring Framework style (e.g. `catch` on its own line).
- Swagger UI is at `/swagger-ui.html`. It is off under the `prod` profile. Its forms open filled in with values
  that work against the demo seed: `@Schema(example)` on request DTO fields, `@Parameter(example)` on query and
  path parameters, the login's demo-account examples on `AuthController.login`, and `OpenApiExamples` for every
  `checkIn`/`checkOut` (a stay 30 days ahead). Give a new request field an example, or Swagger UI sends `"string"`.

## Architecture

The code is grouped by feature, then by layer, under `com.company.project`: `common/` (imports no feature package),
`auth/`, `user/`, `product/`. The flow is controller → service interface (`services/`) → `@Service` implementation
(`services/impl/`, owns the transactions) → Spring Data repository. Controllers depend only on the interface. DTOs are
records, and each feature's `@Component` mapper converts them. Entities never leave the service layer. A layer folder
exists only when it has something in it: `user/` has no controller because its endpoints live in `auth/`.

**Authentication.** HS256 access tokens with one shared secret (`JwtConfig`), plus refresh-token sessions in the
database. Login goes through `AuthServiceImpl`, then `AuthenticationManager` (`DaoAuthenticationProvider` +
`AppUserDetailsService`, where the username is the normalized email), then `SessionService.open`, which stores a
`user_sessions` row and calls `TokenService.issue`. The access token carries `sub` = user id, `email`, a `roles`
claim and `sid` = session id. On each request the OAuth2 resource server verifies the token, then
`SessionTokenValidator` checks that the session is live and the user enabled (one indexed lookup; the decoder chains
it after the standard validators so expired tokens skip it), and `roles` become `ROLE_*` authorities. Public routes
are listed in `SecurityConfig`, and every other route requires a token. Role checks are
`@PreAuthorize("hasRole('ADMIN')")` on controller methods. A new public endpoint needs a matcher in `SecurityConfig`,
and in OpenAPI an empty `@SecurityRequirements` (every operation requires a bearer token by default). The public
account POSTs share `PUBLIC_AUTH_ENDPOINTS`, which also makes the bearer-token resolver ignore `Authorization` there,
so an expired access token never blocks refresh or sign-out. Stripe's webhook (`STRIPE_WEBHOOK`) is public the same
way: Stripe signs it instead of sending a token. `/api/auth/me` (`ProfileController`) reloads the user by
`sub`.

Account rules worth knowing before changing them:

- Refresh tokens and emailed links are `OpaqueTokens` (32 random bytes); only their SHA-256 is stored. Refresh rotates
  the token under a row lock (`findByTokenHashForUpdate`). A match on `previous_token_hash` is a replay: the session is
  revoked and the call answers 401. `SessionServiceImpl.refresh` has `noRollbackFor = BusinessException.class` so that
  revocation commits; `AuthControllerSessionTest#replayedRefreshTokenRevokesTheSession` fails without it.
- Lockout counting is one atomic JPQL `update versioned User` in `UserRepositoryImpl`. `AuthServiceImpl.login` is
  deliberately not transactional, so the failure count commits before the 401. A locked account fails in
  `DaoAuthenticationProvider`'s pre-checks (`LockedException` → the generic 401) before its password is checked.
- Unverified accounts get 403 only after the password matched. Emails go through `AuthMailService`; the only
  implementation, `AuthMailServiceImpl`, writes the links to the log. Mail is sent after the token's transaction, never
  inside one.

**Errors.** Every error is `application/problem+json`. Services throw `BusinessException(HttpStatus, message)` or
`ResourceNotFoundException`, and controllers never build error responses. `GlobalExceptionHandler` maps:

- validation errors → 400 with field names and messages only, never the rejected values
- `DataIntegrityViolationException` and optimistic-lock failures → 409
- unknown sort properties → 400
- authentication failures → 401, with one message for every login failure
- `RateLimitExceededException` → 429 with `Retry-After`

Anything unmatched becomes a generic 500, so a new exception type needs its own handler there. Security errors raised
in the filter chain (missing or invalid token, 403) never reach a controller. `ProblemDetailSecurityHandler` hands them
to MVC's `HandlerExceptionResolver` so the same advice renders them.

**Rate limiting.** `RateLimitFilter` (`common/ratelimit`) sits in the security chain right after `CorsFilter`, so a
429 carries the CORS headers and valid preflights aren't counted. It also runs before authentication and before the
body is read. `SecurityConfig` builds it with `new`; it is not a bean, because Boot would also register a `Filter`
bean in the servlet container, in front of Spring Security. `RateLimitPolicy` holds the matchers: every request
spends from `GENERAL`, and matching requests also spend from `LOGIN`, `REGISTER`, `EMAIL` or `SEARCH` (endpoints in
one policy share a bucket). The numbers are `app.rate-limit.limits.*`. `RateLimiter` keeps one Bucket4j greedy
bucket per policy and client in a bounded Caffeine cache (idle buckets expire after their period, when they'd be
full anyway). The client is `getRemoteAddr()`, an IPv6 address counted as its /64, so behind a proxy
`server.forward-headers-strategy` is required. The filter hands the exception to the resolver like
`ProblemDetailSecurityHandler`. `/actuator/health/**` is never limited. A new expensive or abusable endpoint gets a
matcher in `RateLimitPolicy`.

**Persistence.** Flyway owns the schema (`src/main/resources/db/migration/V{n}__*.sql`). Hibernate only runs
`ddl-auto: validate`, so an entity mapping that doesn't match the SQL fails startup. `BaseEntity` provides:

- an identity `id`
- a `@Version version` for optimistic locking
- `createdAt`/`updatedAt` from Spring Data auditing, set in `JpaAuditingConfig` and truncated to microseconds so a
  write response matches a later read

Other settings: `open-in-view` is off, so load lazy data inside the service transaction. The JDBC time zone is UTC.
Writes use `saveAndFlush` so the generated id, version and timestamps appear in the response. Races such as a duplicate
email are left to unique constraints, which surface as 409.

`WishlistEntry` is the only entity without `BaseEntity`: `wishlists` keeps the schema's composite key `(user_id,
hotel_id)` (`@IdClass` record) and has only `created_at`, which the service sets. It is never built in Java. Saving is
one HQL `insert ... on conflict do nothing` (Hibernate 7 supports it, so no native SQL) and removing is a JPQL bulk
delete, so a repeated or concurrent save or remove never answers 409. The list doesn't filter by listing; it marks
each hotel `available`.

Link tables without columns of their own are mapped read-only on their owner: `Hotel.facilities`
(`hotel_amenities`, an `@Embeddable` with `is_free`), `RoomType.beds` (`room_type_beds`) and `RoomType.features`
(`room_type_amenities`). They are lazy and read only through JPQL joins on the hotel page; search keeps reading the
tables in its native SQL.

**Configuration.** `@ConfigurationPropertiesScan` binds records under `app.*`. `JwtProperties` is `@Validated`, and
`JwtConfig` rejects a secret that isn't base64 of at least 32 bytes. There are three profiles:

- `local`: the default. Imports `.env`; Docker Compose uses `start-only`.
- `test`: set by `@IntegrationTest`. The datasource comes from `@ServiceConnection`.
- `prod`: springdoc is off, and the datasource comes from `SPRING_DATASOURCE_*`.

`AdminBootstrap` (an `ApplicationRunner`) creates an ADMIN+USER account from `APP_BOOTSTRAP_ADMIN_*` if that email does
not exist yet. It never changes an existing account.

**Hotel catalog and search.** The SQL function `norm()` (V3: `unaccent`, lowercase, collapsed spaces) is the only
name normalizer, so Java never re-implements it. The database generates these `destinations` columns:

- `normalized_name` = `norm(name)`
- each `*_key` = `norm()` of its stored spelling, `''` when there is none. The keys are the place's identity
  (unique index, matching addresses), so a stored spelling must always normalize to its key.

Place autocomplete is pg_trgm word similarity on `normalized_name` only. Matching on the places around a name
(`hai chau da nang`) is deferred (C-HOME-07). Suggestions join the COUNTRY row for the country in their label, so no
row keeps a copy of a country's name.

The rest works as follows:

- Repositories follow the services pattern.
  - `XRepository extends JpaRepository<X, Long>, XRepositoryCustom` declares only what Spring Data generates:
    CRUD and derived queries.
  - Every hand-written query is declared in `XRepositoryCustom` and implemented in
    `repository/impl/XRepositoryImpl` with `EntityManager`. No `@Query` on the interfaces.
  - Spring Data finds the impl by the `Impl` suffix and merges it into the `XRepository` bean, so callers inject
    only `XRepository`. Keep the name `XRepositoryImpl`: with a name it doesn't recognise, Spring Data tries to
    derive those methods from their names and startup fails.
  - `DestinationSyncRepository` and `WishlistRepository` have nothing to generate, so each is a plain interface
    plus a `@Repository` impl.
  - Results of hand-written queries are records built by JPQL `select new ...` (`...View`).
- In those impls: JPQL first. `DatabaseFunctionContributor` (`common/persistence`) registers `norm()` for JPQL,
  plus pg_trgm's `word_similar(a, b)` (renders `a <% b`, indexable) and `word_similarity(a, b)`. Hibernate
  renders them as-is, so the indexes still apply. Native SQL (`createNativeQuery`) is only for what JPQL can't
  express well, with a comment saying why:
  - the priced availability check in `HotelRepositoryImpl`: one row per night, `night_price()` per rate plan and
    night, `distinct on` for each room type's cheapest plan, and a running sum that fills the rooms from the cheapest
    room type up. It returns ids, quantities and prices only; JPQL loads the cards, photos and rate descriptions.
    The same CTE chain serves the result list and the filter counts (see the filters bullet below).
  - the bulk `ON CONFLICT` insert, the spelling refresh (both `mode()`) and the advisory lock in
    `DestinationSyncRepositoryImpl`.

  No `JdbcClient` repositories.
- Shared query fragments: `HotelJpql` holds "hotel is listed", "address inside destination" and the hotel card
  select. `HotelSql` holds the SQL twins used by the native queries; change both together.
- Hibernate's PostgreSQL dialect renders `like ... escape ''`. LIKE on typed text must therefore end with an explicit
  backslash escape (`escape '\\'` in the Java text block, `escape '\'` in SQL), because the service escapes `\`, `%`
  and `_` with backslashes. Don't end a text-block line with a space before the closing quotes (Java strips it);
  write `\s`.
- `DestinationSyncJob` runs the sync on startup and on `app.destinations.sync.cron`. The sync inserts new places and
  refreshes their spellings (most common among the place's hotels, chosen once per place and reused by the places
  inside it) and `hotel_count`. Admin-owned columns (`name`, `image_url`, `featured_rank`) are never written by it,
  and the `Destination` entity marks the sync-owned columns `updatable = false`.
- Search is stateless. Every criterion is a GET parameter, and the browser keeps the "last search" itself.
- The SQL function `night_price(plan_id, night)` (V5) is the only definition of a night's price: date override, then
  the highest-priority rule, then the base price. Cart and booking must call it too, never re-implement it. Closed
  nights and minimum stays live in `rate_plan_prices` beside it. A room counts as available only when an active rate
  plan in the hotel's currency can sell every night. Hibernate's default `TIME` mapping shifts values by the JVM's
  zone (because `jdbc.time_zone` is UTC), so wall-clock times such as `Hotel.checkInFrom` use
  `@JdbcType(LocalTimeJdbcType.class)`.
- Filters (C-SRCH-02) are predicates over the rows of that chain, with fixed aliases: `c` = `hotel_rows` (hotel
  columns plus a `facilities` code array), `o` = `offers` (rate offer plus board code, cancellation and the room
  type's bed and feature code arrays), `v` = `available`/`results`. Hotel predicates narrow `candidate_hotels`
  before pricing. Offer predicates narrow `scoped_offers`, so the offer is the cheapest mix of what is left. The fill
  (`cheapest_rates` → `available`) is partitioned by a scope (`facet`, `facet_value`): the list uses `'ALL'`, and the
  counts add one scope per board/cancellation/bed/room-feature option. They also drop the hotel predicates from the
  candidates, because a hotel failing one filter still counts for that filter's options.
  `SearchControllerFilterCountsTest#everyCountMatchesTheResultListWithThatOptionPicked` keeps the two paths equal.
- `HotelFilterSql` renders only the active filters and collects their parameters as it renders them. An empty list
  would become `in ()`, and Hibernate rejects binding a parameter the text doesn't use. That's also why `results`
  always selects `:priceCurrency` (`in_price_currency`). Hibernate expands a list parameter inside `array[...]` into a
  row value (`array[(?, ?)]`), so array columns are matched with `unnest(...) ... in (:list)`, never `array[:list]`.
- The SQL function `free_cancellation_until()` (V6) is the twin of `FreeCancellation.until` (the cancellation filter
  uses one, the cards the other); change both. `FreeCancellationSqlTest` compares them. The service captures one
  `now` and passes it to both.
- The hotel page's room list (`HotelRepositoryImpl.findRoomRates`) reuses the chain's first CTEs, so it and the search
  agree on what can be sold: `NIGHTS`, `FREE_ROOMS` (+ `FREE_ROOMS_END`), `RATE_OFFERS`, and `ROOM_TYPE_FITS`, the
  only definition of "a room type holds one room's guests" (search filters by it, the room list reports it). Change
  them once; `HotelControllerRoomsTest#theCheapestFittingRateIsTheSearchCardsOffer` keeps the two equal. Its
  `night_prices` column (`bigint[]`, read as `Long[]`) feeds `CancellationSchedule`, which turns a policy's rules into
  priced tiers; the free deadline stays `FreeCancellation.until`, and the first paid tier starts there.
- `SearchCriteria.today()` (the date at UTC−12) is the "today" every stay is checked against, in search, on the
  hotel page and in the cart (its expiry job deletes lines checking in before it).
- The cart (`CartServiceImpl`) never prices on its own. Every read and write calls `findRoomRates` once per (hotel,
  dates, guests per room) and picks its rate plans' rows, so the cart, the room list and search agree;
  `CartControllerPricesTest#aLineCostsWhatTheHotelPageAsksForTheSameRateAndStay` keeps it that way. The rules
  maintainers would otherwise break:
  - A group (one hotel, one date range) is one future booking, so its lines of one room type share `roomsLeft`.
  - Every write locks the user's `carts` row (`findByUserIdForUpdate`) before checking rooms or merging lines.
  - One line per (cart, rate plan, dates, adults, child ages) is enforced by `cart_items_line_key`, so the ages must
    be stored sorted (`RoomGuests` sorts them).
  - `CartItem.childAges` (`Short[]` ↔ `smallint[]`) is the first array-mapped entity field.
  - The merge catches `BusinessException` per item, which is safe only because every check throws before anything
    is written. Never catch database exceptions there.
- Checkout (`BookingServiceImpl`) prices a cart group with the cart's rules (one `findRoomRates` call per guests
  per room, a group's lines of one room type sharing `roomsLeft`) and never prices on its own either.
  `BookingControllerQuoteTest#aLineCostsWhatTheCartShowsForIt` keeps the quote and the cart equal. The rules
  maintainers would otherwise break:
  - `TaxCalculator` (`tax/model`) is the only place tax amounts are computed: per rule, only the nights inside its
    validity, rounded half up once. `PERCENT` after the discount scales the price by `(subtotal − discount) /
    subtotal` inside the one division. A fixed tax in another currency refuses checkout (409) rather than
    under-charge.
  - A booking write takes its locks in one order: the user's `carts` row, the discount code row
    (`findLiveByCodeForUpdate`, so usage limits hold), then `room_inventory` rows by room type and night. Keep that
    order in any new booking write (payment, cancellation), or checkouts can deadlock.
  - Rooms are held by `RoomInventoryRepository.hold`, a native upsert that adds to `booked_rooms` only where the
    rooms fit and the night isn't closed, creating missing rows from `room_types.total_rooms`; fewer rows written than
    nights means "taken", and the caller rolls back. `release` is its twin (locks in the same order).
    `booked_rooms` counts Pending and Confirmed bookings, so search, the hotel page and the cart drop held rooms with
    no change to their SQL. `BookingConcurrencyTest` books the last room from two threads.
  - The total the customer saw comes back as `expectedTotal`; any difference is 409 and nothing is written.
  - A booking copies what was sold: per room the room name, board code, cancellation policy (`jsonb`, the
    `CancellationTerms` record via `@JdbcTypeCode(SqlTypes.JSON)`) and nightly prices (`booking_room_nights`, an
    `@ElementCollection` of `BookingNight`); per tax its rule's name and amount; the partner's commission rate
    (native read: `partners` has no entity). `commission_minor` stays 0 until payouts.
  - Status rules are V10's triggers: inserts must be `PENDING`, updates must follow a lifecycle arrow, and every
    change is logged in `booking_status_history`. Who and why come from the transaction-local settings
    `app.changed_by` and `app.status_reason` (`BookingRepository.setStatusReason`, called before the change);
    without `app.changed_by` an insert is logged as the booking's user and an update as the system. Hibernate
    writes every column on update, so the triggers compare old and new status.
  - `BookingHoldExpiryJob` expires Pending bookings past `hold_expires_at` in batches of 100, locked with `skip
    locked`; it releases their rooms and deletes their `discount_redemptions`. It skips bookings with an open payment
    (see the payment bullets).
  - Discount codes are stored lowercased (`DiscountCode.normalize`); one per booking; uses are counted from
    `discount_redemptions`. "First booking only" counts the user's Pending, Confirmed, Checked-in, Checked-out and
    No-show bookings. `Money` has `@NotNull` components so a request can `@Valid` the total it sends back.
- Payment (`payment/`, C-PAY-01/02/05) and the booking page, emails and cancellation (`booking/`, C-PAY-03,
  C-MYBK-03). The rules maintainers would otherwise break:
  - **Only Stripe's state confirms a booking.** The webhook verifies the signature (`StripeSignatureVerifier`, over
    the raw body read as a String), logs the event once in `stripe_events`, then re-reads the PaymentIntent or refund
    from Stripe and applies that state (`PaymentServiceImpl.apply`, `RefundServiceImpl.applyRefund`). Never act on
    the event's own copy or on the browser's redirect; repeats and out-of-order delivery are harmless because of this.
    `Payment.apply` never leaves SUCCEEDED or CANCELED.
  - **`booking/` owns the `payments` and `refunds` entities** (its expiry, page and cancellation read them);
    `payment/` holds everything Stripe and depends on `booking/`, never the reverse. `PaymentGateway` is the only
    class calling Stripe's API; tests replace it with `FakePaymentGateway`.
  - **Lock order for a write to an existing booking: its row first** (`findByReferenceAndUserIdForUpdate` /
    `findByIdForUpdate`), then its payments, refunds and `room_inventory`. `apply` reads the payment's booking id by
    query (`findBookingIdByPaymentIntentId`), locks the booking, then loads the payment, so it never works on a stale
    copy. Checkout never locks an existing booking row, so this can't deadlock with it.
  - **Before a Pending booking leaves Pending for any reason other than payment, cancel its open PaymentIntent on
    Stripe.** `PaymentHoldExpiryJob` does it for the hold's end, and `findExpiredHoldsForUpdate` excludes bookings
    with an open payment (`PaymentStatus.OPEN`), so rooms are released only once Stripe can no longer charge them.
    Any future Pending → Cancelled path must do the same.
  - One PaymentIntent per booking: created under the booking's row lock with idempotency key `payment-{reference}`
    (references are random, so a reset database never reuses a key), and `one_open_payment` is the backstop. Refunds use
    `refund-{reference}` (one cancellation per booking). `StripeAmounts` is the only minor-unit conversion (ISK and UGX
    ×100, three-decimal currencies in tens).
  - Stripe calls never run while a transaction holds booking or inventory locks, except the PaymentIntent create
    (one row, on purpose). Mail and refunds go out after commit: `BookingConfirmed` and `BookingCancelled` are
    published inside the transaction and handled by `@TransactionalEventListener(AFTER_COMMIT)` listeners, whose reads
    and writes use `REQUIRES_NEW` (the committed transaction's resources are still bound).
  - `CancellationCharges` is the only penalty and refund computation (preview and cancel share it); a room's penalty
    is its stored policy's `CancellationSchedule` tier in effect, a non-refundable room its whole stay. Cancel calls
    `RoomInventoryRepository.release` before `Booking.cancel`, because release reads the booking's ACTIVE rooms.
  - A booking's response is built from what it stored (`BookingResponses` + the entity-based
    `BookingMapper.toBooking`), for the create response, the page and the emails alike. Room terms come from
    `HotelMapper.toCancellation`, shared with the hotel page.
- Price filter and sort compare one currency (`HotelFilter.priceCurrency`: the request's `currency`, else the most
  common selling currency of the destination's listed hotels) until currency conversion exists (C-SRCH-05).
- Under the `local` profile only, Flyway also loads `db/seed/` (demo hotels, plus the two demo accounts that
  Swagger's login examples use).

## Tests

- Full-app tests use only `@IntegrationTest` (`src/test/.../support`), so the whole run shares one Spring context and
  one Postgres container. Adding `@MockitoBean`, `@TestPropertySource` or similar to a test class starts another
  context and another container.
- Full-app tests share the database and nothing is rolled back. Create your own data (`TestAuth.uniqueEmail()`) and
  assert only on that, never on counts or on an empty table.
- `TestAuth` provides `userToken(mvc)` (registers, verifies and signs in), `registerVerified`, `adminToken(mvc)` (the
  bootstrap admin from `application-test.yml`) and `bearer(token)`. Tests use the AssertJ `MockMvcTester` API.
- `@IntegrationTest` imports `RecordingAuthMailService.Config`, a `@Primary` mailer that keeps the last link per email.
  Read links with its static `verificationToken(email)` / `resetToken(email)`; don't add a mailer mock (that would
  start a second context). `app.auth.email-cooldown` is 0 in tests. It also imports `RecordingBookingMailService.Config`
  (booking emails per reference, `failNext()`) and `FakePaymentGateway.Config`, the `@Primary` Stripe: autowire
  `FakePaymentGateway`, move intents along (`succeed`, `decline`, `failRefund`, `failNextCall`) and send the webhook
  with `StripeWebhooks.send` (signed with the test profile's secret, so the real verifier runs). `BookingClient` (public
  for the payment tests) books (`bookRooms`), pays (`pay`) and calls any booking path. `PaymentHoldExpiryTest`
  cancels every test's open payments of past holds and `RefundService.retryUnsent()` resends every test's unsent
  refunds; a test that needs to be first in that batch dates its row older than the rest (`1 day`).
- `CatalogFixtures` inserts partners, hotels, room types, rate plans, rules, inventory, photos and tax rules with
  `JdbcClient` (test code only), since no hotel write API exists yet. `roomType` adds a sellable default rate plan
  (`bareRoomType` none). Tax rules apply to a whole country, so tax tests use their own `XA`–`XZ` codes, never `VN`.
  Give every place and hotel a `uniqueSuffix()`, call `DestinationSyncService.sync()` yourself (the `test` profile
  disables the schedule), and assert only on your own slugs. Featured and popular lists are shared by all tests, so
  assert relative order, not positions. `HotelBuilder` sets stars, type, distance and currency; `hotelAmenity`,
  `roomAmenity` and `bed` link V6 reference rows. A hotel in another currency needs `bareRoomType` plus a
  `ratePlan(...).currency(...)` in that currency. `review(...)` inserts a review of `booking(userId, hotelId)`, a past
  stay inserted Pending and walked to Checked-out through the status triggers, written by `user()`, an account that
  can't sign in. `defaultRatePlan` returns the plan
  `roomType` added. `cartItem` writes cart lines the API refuses (past check-ins); a test that calls
  `deleteCheckedInBefore` or `removeExpiredItems` deletes every test's past lines, so only past-dated fixtures may be
  left for it. `BookingHoldExpiryTest` likewise expires every test's past holds, so no other test leaves a
  hold in the past. `discountCode(...)` builds codes (give each a `uniqueSuffix()`), and `taxRule(country, name,
  calcType, ...)` writes any tax type.
- The `test` profile exempts `127.0.0.1` (MockMvc's address) from rate limits. Buckets live as long as the shared
  context, so a rate limit test sends from its own address (`request.setRemoteAddr`, `203.0.113.x` in
  `RateLimitFilterTest`). `general` and `search` refill within the test's own run, so their tests send until the
  first 429 rather than expecting it at an exact count.
- MockMvc encodes `%` in a URI template again; send text with `%` through `.param(...)`.
- JDBC reads `smallint` back as `Integer`, not `Short`.
- A `@DataJpaTest` needs `@AutoConfigureTestDatabase(replace = NONE)`, `@Import({TestcontainersConfiguration.class,
  JpaAuditingConfig.class})` and `@ActiveProfiles("test")`. Without the auditing import, inserts fail on the not-null
  timestamp columns.
- A test class sits in the same package as the class it covers.
- The `postgres:18-alpine` tag appears in both `docker-compose.yml` and `TestcontainersConfiguration`. Change both
  together.

## Conventions

- Never edit a migration that has run anywhere. Add `V{next}__...sql`. New tables copy the standard columns from
  `V2__create_product_tables.sql` (identity `id`, `version bigint not null default 0`, `created_at`/`updated_at
  timestamptz not null`).
- Entities extend `BaseEntity` (except `WishlistEntry`, see Persistence) and use `@Getter` and
  `@NoArgsConstructor(access = PROTECTED)`. A static `create(...)` factory and domain methods such as `update(...)`
  hold the field rules (trimming, normalizing). Never put Lombok
  `@Data`, `@ToString` or `@EqualsAndHashCode` on an entity: `BaseEntity` defines equality by id.
- Records and properties that carry secrets override `toString()` to mask them.
- Normalize emails with `User.normalizeEmail` (trim and lowercase) before every lookup and insert. The unique
  constraint relies on it.
- Passwords use the delegating encoder (BCrypt, stored with a `{bcrypt}` prefix). The 72-byte BCrypt limit is checked
  in bytes (`UserService.exceedsPasswordByteLimit`) on top of `@Size`, because `@Size` counts characters. Login treats
  an over-long password as bad credentials.
- List endpoints return `PageResponse.from(page)`, never a raw `Page`. The page size is capped at 100 globally.
- README "Known limitations" lists gaps left in on purpose: emails are only logged, rate limits are in memory per
  instance and per IP (many IPs can still lock known accounts), registration's 409 reveals existing emails, concurrent
  refreshes sign a device out. It also
  covers the limits of the hotel search (no currency conversion or "recommended" sort, room prices only with a tax
  flag, even split of guests per room, the cost of the filter counts), of the hotel page (read-only reviews and
  stored ratings, per-room `FIXED` penalties, exact `roomsLeft`), of the cart (merged lines start at the merge's
  price, one room-list query per distinct stay, empty cart rows kept), of checkout (hotel currency only; commission
  amount not computed; hold rows copy `total_rooms`; codes and taxes written by SQL) and of payment (booking emails
  only logged and lost on a crash after commit; cards only; 100 expired-hold cancels per minute; misplaced payments
  and failed refunds handled by hand; whole-booking, Confirmed-only cancellation counted from the hotel's current
  check-in time; `stripe_events` payloads kept).
- JPQL bulk updates bypass auditing and versioning: write `update versioned X` and set `updatedAt` yourself (see
  `UserSessionRepositoryImpl`).
- Public endpoints need a `permitAll` matcher in `SecurityConfig` and an empty `@SecurityRequirements` on the
  operation. Admin endpoints live under `/api/admin/**` with a class-level `@PreAuthorize("hasRole('ADMIN')")`.
  Every GET under `/api/hotels/**` is public (the hotel page), so authenticated hotel endpoints (owners, admins) need
  another prefix.
