# Development guide

How the code is organised and tested, every setting, and notes for production and for copying this project as a
template. Setup and running are in the [README](../README.md); endpoints and feature rules are in
[features.md](features.md).

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
├── booking/                             checkout: quote, Pending booking with a room hold, guest details; the
│   │                                    booking page, cancellation, and the payment and refund rows it owns
│   ├── controller/  config/  model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)
│   ├── expiry/                          scheduled expiry of unpaid holds
│   └── notification/                    confirmation and cancellation emails, after commit
├── payment/                             Stripe: payment start and status, the webhook, refunds (depends on booking/)
│   ├── controller/  config/  model/  repository/ (+ impl/)  dto/  mapper/  services/ (+ impl/)
│   ├── expiry/                          cancels the payments of expired holds on Stripe
│   └── refund/                          sends refunds after commit and resends unsent ones
├── discount/                            discount codes and their uses
│   └── model/  repository/ (+ impl/)  services/ (+ impl/)
└── review/                              hotel reviews, read on the hotel page
    └── controller/  model/  repository/  dto/  mapper/  services/ (+ impl/)
src/main/resources/
├── application.yml                      defaults for every profile
├── application-local.yml                local dev (the default profile): .env, Docker Compose, demo seed
├── application-prod.yml                 production overrides
├── db/migration/                        V1 users, V2 products, V3 hotel catalog + norm(), V4 tokens + sessions,
│                                        V5 rates + taxes + night_price(), V6 amenities + beds +
│                                        free_cancellation_until(), V7 wishlists, V8 hotel policies + reviews,
│                                        V9 carts, V10 bookings + discount codes + status triggers,
│                                        V11 payments + refunds + Stripe events
└── db/seed/R__demo_catalog.sql          demo hotels and accounts, local profile only
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
| `RateLimitPolicyTest` | unit | the cart's priced calls spend from `search`, quote, booking and payment start from `checkout`, removing a line or polling a payment from neither, search endpoints keep theirs |
| `CartControllerTest` | full app | every line and group field, the same line adds up (ages in any order), groups by hotel and dates, 404/400/409 checks leaving the cart unchanged, rooms shared by a group's lines of one room type, full cart, one cart per user, sign-in required |
| `CartControllerPricesTest` | full app | changed price with old and new, not enough rooms, unavailable (closed night, inactive plan, unlisted hotel), passed check-in, same price and terms as the hotel page, group total of available lines only |
| `CartControllerEditTest` | full app | quantity edit keeps the notice, new dates move the line at today's price, dates onto another line merge them, rejected edits change nothing, other users' lines, idempotent remove |
| `CartControllerMergeTest` | full app | browser items added, larger quantity kept, second merge changes nothing, skipped items with index and reason, merged lines share the rooms check, item limit |
| `CartItemRepositoryTest` | full app | `child_ages` mapping and sorting, one line per rate/stay/guests, the expiry delete, the cart row lock |
| `CartServiceImplTest` | unit | empty cart reads only its lines, passed lines aren't priced, one room-list call per stay and guests, not-enough-rooms per group and room type |
| `CartExpiryTest` | full app | passed lines reported, then deleted; today's check-in kept |
| `BookingControllerQuoteTest` | full app | lines, subtotal, taxes and total of a group; same prices and terms as the cart; included taxes listed not added; several hotels or dates (400), other users' lines (404), unbookable lines (409), rooms shared by a room type's lines; booker from the profile; sign-in required |
| `BookingControllerDiscountTest` | full app | every refusal message (unknown, inactive, deleted, dates, hotels, currency, minimum spend, usage limits, first booking), percent cap with tax on the discounted price, fixed amount never past the subtotal, any case |
| `BookingControllerTest` | full app | Pending booking with reference, hold, guest details, arrival time, rooms, nights, taxes and commission; lines leave the cart; held rooms leave the hotel page; changed total refused until confirmed, nothing written; guest details and terms validation; lead guests; code use recorded and counted; same lines booked once |
| `BookingConcurrencyTest` | full app | two customers booking the last room at once: one booking, one 409, never overbooked (repeated 5 times) |
| `BookingHoldExpiryTest` | full app | an unpaid hold expires, gives back its rooms and code use, is logged as the system's; a live hold stays |
| `BookingStatusRulesTest` | full app | a booking starts Pending, only the lifecycle's arrows, every change logged, unchanged status logs nothing, who and why from the transaction's settings |
| `BookingMappingTest` | full app | a booking with rooms, nights (element collection), jsonb cancellation terms, taxes and arrival time reads back as written |
| `RoomInventoryRepositoryTest` | full app | a hold creates missing nights, adds to existing ones until full, refuses closed nights and more rooms than the type has |
| `TaxCalculatorTest`, `BookingReferenceTest` | unit | each tax type, validity per night, after or before the discount, included share, rounding once, other currency refused; reference format |
| `BookingPaymentControllerTest` | full app | one PaymentIntent per booking for its total, the same secret on every start and after a decline, concurrent starts, 404 not yours, 409 hold ended or not Pending, nothing to pay confirms at once, 503 with nothing written |
| `StripeWebhookControllerTest` | full app | a signed success confirms (logged by the system), bad or missing signature changes nothing, repeats confirm once, decline then retry, a late decline can't undo a success, success for an Expired booking only recorded, unknown intents ignored, a failed event is handled when sent again |
| `PaymentHoldExpiryTest` | full app | an open payment is canceled before its booking expires and gives its rooms back; an intent that already succeeded confirms; while Stripe is down the rooms stay held |
| `BookingControllerReadTest` | full app | the booking page: hotel address and contact, rooms, guests, terms, breakdown equal to the booking's, payment; 404 not yours |
| `BookingMailListenerTest` | full app | a paid booking sends one confirmation and one hotel notice with the whole booking, never twice, also with nothing to pay; a failed email never fails the payment |
| `BookingControllerCancelTest` | full app | free, penalty and non-refundable cancellations, rooms back on sale, refund on Stripe and its status, stale refund refused, only Confirmed bookings of your own, unsent refunds retried or given up after a day, a failed refund shown, concurrent cancels refund once |
| `CancellationChargesTest`, `StripeAmountsTest`, `StripeSignatureVerifierTest`, `StripePaymentGatewayTest` | unit | per-room penalties and the proportional refund; Stripe's minor units; signature checks (body, secret, age, missing); 503 without keys |

Each test sits in the package of the class it covers (e.g. `product/controller/ProductControllerTest`).
Full-app tests use `@IntegrationTest` (`src/test/.../support`). Every class shares one Spring context and one
container, so the suite stays fast. Tests create their own data with unique emails and never assume an empty database.
`@IntegrationTest` swaps the logging mail service for `RecordingAuthMailService`, so tests follow emailed links like a user;
`TestAuth.userToken` registers, verifies and signs in. It also swaps the booking mailer for `RecordingBookingMailService`
(emails per booking reference) and Stripe for `FakePaymentGateway`: tests move an intent along (`succeed`, `decline`,
`failNextCall`) and send the webhook Stripe would, signed by `StripeWebhooks` with the test secret, so the signature
check stays real. `BookingClient.pay` does all three. `PaymentHoldExpiryTest` cancels every test's open payments of
past holds, like `BookingHoldExpiryTest`.
Hotel-catalog tests build hotels with `CatalogFixtures` and unique place names, then call the destination sync
themselves (the `test` profile never schedules it). `CatalogFixtures.roomType` also adds a sellable rate plan
(`bareRoomType` adds none). `hotelAmenity`, `roomAmenity` and `bed` attach V6 reference rows. Tax rules apply to a whole country, so tax tests use their own user-assigned country codes
(`XA`–`XZ`); `taxRule` writes any calc type. `review` inserts reviews of a past stay from `booking(userId, hotelId)`,
which inserts a booking Pending and walks it to Checked-out through the status triggers; the reviewer is a `user()`
that can't sign in. `defaultRatePlan` returns the plan `roomType` added, `cartItem` writes cart lines the API would
refuse (check-ins already past), `discountCode` builds a code, and `userId` reads an account's id; cart and booking
controller tests sign up through their `CartClient` and `BookingClient`. `BookingHoldExpiryTest` expires every
test's past holds, so no other test leaves one. The `test` profile exempts MockMvc's `127.0.0.1` from rate limits, so `RateLimitFilterTest` sends
from its own `203.0.113.x` addresses. Unit tests use Mockito and start no Spring context.

`TestProjectApplication` (in `src/test`) runs the app against a Testcontainers database instead of Docker Compose.

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
| `APP_BOOKING_EXPIRY_CRON` | `0 * * * * *` | when to expire Pending bookings whose room hold passed, and first cancel their started payments on Stripe (Spring cron, six fields); `-` disables it |
| `APP_STRIPE_SECRET_KEY` | empty (card payments answer 503) | Stripe API secret key, `sk_test_...` in test mode |
| `APP_STRIPE_WEBHOOK_SECRET` | empty (the webhook answers 503) | the webhook endpoint's signing secret, `whsec_...`; `stripe listen` prints one locally |
| `APP_REFUND_RETRY_CRON` | `30 * * * * *` | when to resend refunds that didn't reach Stripe (Spring cron, six fields); `-` disables it |
| `APP_RATE_LIMIT_ENABLED` | `true` | `false` turns every rate limit off, e.g. when a gateway limits instead |
| `APP_RATE_LIMIT_EXEMPT_ADDRESSES` | empty | comma-separated client addresses never limited, e.g. an internal monitor; `127.0.0.1` in a local `.env` keeps you from limiting yourself |

Access token lifetime is `app.jwt.access-token-ttl` (default `15m`) in `application.yml`. Account settings are
`app.auth.*` there: `refresh-token-ttl` 1d, `remember-me-ttl` 30d, `email-verification-ttl` 24h,
`password-reset-ttl` 30m, `email-cooldown` 1m, and `lockout` (`max-attempts` 5, `window` 15m, `duration` 15m). Search limits are `app.search.*`
there (`max-nights` 30, `max-days-ahead` 365, `max-rooms` 8, `max-adults` 30, `max-children` 10,
`rating-sort-min-reviews` 10); cart quantities and guests follow them, and `app.cart.max-items` (20) caps the lines
of one cart. `app.booking.hold` (15m) is how long a new booking holds its rooms. Rate limits are `app.rate-limit.limits.*` there (`capacity` per `period`, values in the
[rate limit table](features.md#endpoints)), and `max-tracked-clients` (50000) caps how many clients each limit remembers.

## Production notes

- Run with `SPRING_PROFILES_ACTIVE=prod`. This turns off Swagger UI and `/v3/api-docs`.
- Provide the database with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
  Docker Compose support is dev-only and is not included in the packaged jar.
- Build a jar with `./mvnw package` (`target/spring-base-0.0.1-SNAPSHOT.jar`), or a container image with
  `./mvnw spring-boot:build-image`.
- Provide `APP_JWT_SECRET` from a secret manager. Changing it invalidates every issued token.
- Set `APP_FRONTEND_URL` to the web app, and wire a real `AuthMailService` and `BookingMailService` before real users
  sign up (see [Known limitations](features.md#known-limitations-deliberate-for-a-base-project)).
- Provide `APP_STRIPE_SECRET_KEY` (live mode) and `APP_STRIPE_WEBHOOK_SECRET` from a secret manager, and register the
  webhook endpoint with the API version and events listed under [Payment and cancellation](features.md#payment-and-cancellation).
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
   need them (`hotel/`, `rate/`, `tax/`, `destination/`, `search/`, `wishlist/`, `review/`, `cart/`, `booking/`,
   `discount/`,
   `SchedulingConfig`, `V3__create_hotel_catalog_tables.sql`, `V5__create_rate_and_tax_tables.sql`,
   `V6__create_amenity_and_bed_tables.sql`, `V7__create_wishlist_table.sql`,
   `V8__create_hotel_policy_and_review_tables.sql`, `V9__create_cart_tables.sql`,
   `V10__create_booking_and_discount_tables.sql`, `payment/`, `V11__create_payment_tables.sql`, the `stripe-java`
   dependency, `db/seed/`, the `app.search` / `app.destinations` / `app.cart` / `app.booking` / `app.stripe` /
   `app.payment` settings, their `SecurityConfig` matchers and the cart and booking paths in `RateLimitPolicy`). If the copy is
   brand new and has never run, you may renumber migrations.
4. `git init`.
