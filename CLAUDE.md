# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A REST API base project: Java 21, Spring Boot 4.1 (Spring Framework 7, Security 7.1, Hibernate 7), PostgreSQL 18,
Flyway, springdoc, Lombok, Testcontainers. It has JWT auth, role-based access, and RFC 9457 errors. `product` is a
sample feature that shows every convention end to end. `README.md` has the endpoint table, env var table, production
notes and the "Adding a feature" checklist.

`USER_STORIES.md` and `DATABASE-SCHEMA.md` (dbdiagram.io DBML, 54 tables) describe the Hotel Booking Platform this base
is meant to become. Implemented so far: the homepage stories C-HOME-01/02/03 (`search/`, `destination/`, migration V3)
and the admin slice of A-CONT-01 that curates destinations. The schema doc says it was generated from a `schema.sql`
that is not in this repo.

**Decided (2026-10-21): new tables keep the schema doc's table and column names but follow this project's
conventions.** That means identity `bigint` ids, a `version` column, and `created_at`/`updated_at` set by auditing.
Enum-like columns are `varchar` + CHECK holding the Java enum names (UPPERCASE). `char(n)` becomes `varchar(n)` + a
regex CHECK, and `citext` becomes `varchar` stored lowercased. Tables with a composite natural key in the doc
(`room_inventory`) keep it without id/version, like `user_roles`. A foreign key to a table that doesn't exist yet
(e.g. `hotels.currency` → `currencies`) is added by the migration that creates that table. **Decided (2026-10-22):**
`destinations` also stores spellings (`state_province`, `city`, `district`, `ward`), and its `*_key` columns
are generated from them. The doc's `destinations` table was edited by hand to match,
so it no longer matches that `schema.sql`. Still open for later
stories: roles (`customer/hotel_owner/hotel_staff/admin` vs code's `USER/ADMIN`) and refresh-token sessions. Ask
before changing either.

## Commands

```bash
cp .env.example .env    # then set APP_JWT_SECRET=$(openssl rand -base64 32); the app will not start without it
./mvnw spring-boot:run  # profile "local": reads .env, runs `docker compose up` for Postgres, port 8080
./mvnw verify           # full suite; Docker must be running (Testcontainers)
./mvnw test -Dtest=TokenServiceTest                                    # one class
./mvnw test -Dtest='ProductControllerTest#userCanListAndReadProducts'  # one method
./mvnw package          # target/spring-base-0.0.1-SNAPSHOT.jar
docker compose stop     # the app leaves Postgres running; `docker compose down -v` deletes the data
```

- Mockito unit tests run without Docker. Any `@IntegrationTest` or `@DataJpaTest` class needs Docker.
- `TestProjectApplication` (in `src/test`) runs the app against a Testcontainers Postgres instead of Compose.
- No linter or formatter is configured. Code uses tabs and Spring Framework style (e.g. `catch` on its own line).
- Swagger UI is at `/swagger-ui.html`. It is off under the `prod` profile.

## Architecture

The code is grouped by feature, then by layer, under `com.company.project`: `common/` (imports no feature package),
`auth/`, `user/`, `product/`. The flow is controller → service interface (`services/`) → `@Service` implementation
(`services/impl/`, owns the transactions) → Spring Data repository. Controllers depend only on the interface. DTOs are
records, and each feature's `@Component` mapper converts them. Entities never leave the service layer. A layer folder
exists only when it has something in it: `user/` has no controller because its endpoints live in `auth/`.

**Authentication.** Stateless, HS256 with one shared secret (`JwtConfig`). Login goes through `AuthServiceImpl`, then
`AuthenticationManager` (`DaoAuthenticationProvider` + `AppUserDetailsService`, where the username is the normalized
email), then `TokenService.issue`. The token carries `sub` = user id, `email`, and a `roles` claim. On each request the
OAuth2 resource server verifies the token and maps `roles` to `ROLE_*` authorities. Public routes are listed in
`SecurityConfig`, and every other route requires a token. Role checks are `@PreAuthorize("hasRole('ADMIN')")` on
controller methods. A new public endpoint needs a matcher in `SecurityConfig`, and in OpenAPI an empty
`@SecurityRequirements` (every operation requires a bearer token by default). `/api/auth/me` reloads the user from the
database by `sub`.

**Errors.** Every error is `application/problem+json`. Services throw `BusinessException(HttpStatus, message)` or
`ResourceNotFoundException`, and controllers never build error responses. `GlobalExceptionHandler` maps:

- validation errors → 400 with field names and messages only, never the rejected values
- `DataIntegrityViolationException` and optimistic-lock failures → 409
- unknown sort properties → 400
- authentication failures → 401, with one message for every login failure

Anything unmatched becomes a generic 500, so a new exception type needs its own handler there. Security errors raised
in the filter chain (missing or invalid token, 403) never reach a controller. `ProblemDetailSecurityHandler` hands them
to MVC's `HandlerExceptionResolver` so the same advice renders them.

**Persistence.** Flyway owns the schema (`src/main/resources/db/migration/V{n}__*.sql`). Hibernate only runs
`ddl-auto: validate`, so an entity mapping that doesn't match the SQL fails startup. `BaseEntity` provides:

- an identity `id`
- a `@Version version` for optimistic locking
- `createdAt`/`updatedAt` from Spring Data auditing, set in `JpaAuditingConfig` and truncated to microseconds so a
  write response matches a later read

Other settings: `open-in-view` is off, so load lazy data inside the service transaction. The JDBC time zone is UTC.
Writes use `saveAndFlush` so the generated id, version and timestamps appear in the response. Races such as a duplicate
email are left to unique constraints, which surface as 409.

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
  - `DestinationSyncRepository` has nothing to generate, so it is a plain interface plus a `@Repository` impl.
  - Results of hand-written queries are records built by JPQL `select new ...` (`...View`).
- In those impls: JPQL first. `DatabaseFunctionContributor` (`common/persistence`) registers `norm()` for JPQL,
  plus pg_trgm's `word_similar(a, b)` (renders `a <% b`, indexable) and `word_similarity(a, b)`. Hibernate
  renders them as-is, so the indexes still apply. Native SQL (`createNativeQuery`) is only for what JPQL can't
  express well, with a comment saying why:
  - the per-night availability check in `HotelRepositoryImpl`. It returns hotel ids only; JPQL loads the cards.
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
- Under the `local` profile only, Flyway also loads `db/seed/` (demo hotels).

## Tests

- Full-app tests use only `@IntegrationTest` (`src/test/.../support`), so the whole run shares one Spring context and
  one Postgres container. Adding `@MockitoBean`, `@TestPropertySource` or similar to a test class starts another
  context and another container.
- Full-app tests share the database and nothing is rolled back. Create your own data (`TestAuth.uniqueEmail()`) and
  assert only on that, never on counts or on an empty table.
- `TestAuth` provides `userToken(mvc)`, `adminToken(mvc)` (the bootstrap admin from `application-test.yml`) and
  `bearer(token)`. Tests use the AssertJ `MockMvcTester` API.
- `CatalogFixtures` inserts partners, hotels, room types, inventory and photos with `JdbcClient` (test code only),
  since no hotel write API exists yet. Give every place and hotel a `uniqueSuffix()`, call
  `DestinationSyncService.sync()` yourself (the `test` profile disables the schedule), and assert only on your own
  slugs. Featured and popular lists are
  shared by all tests, so assert relative order, not positions.
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
- Entities extend `BaseEntity` and use `@Getter` and `@NoArgsConstructor(access = PROTECTED)`. A static `create(...)`
  factory and domain methods such as `update(...)` hold the field rules (trimming, normalizing). Never put Lombok
  `@Data`, `@ToString` or `@EqualsAndHashCode` on an entity: `BaseEntity` defines equality by id.
- Records and properties that carry secrets override `toString()` to mask them.
- Normalize emails with `User.normalizeEmail` (trim and lowercase) before every lookup and insert. The unique
  constraint relies on it.
- Passwords use the delegating encoder (BCrypt, stored with a `{bcrypt}` prefix). The 72-byte BCrypt limit is checked
  in bytes (`UserService.exceedsPasswordByteLimit`) on top of `@Size`, because `@Size` counts characters. Login treats
  an over-long password as bad credentials.
- List endpoints return `PageResponse.from(page)`, never a raw `Page`. The page size is capped at 100 globally.
- README "Known limitations" lists gaps left in on purpose: no refresh tokens or revocation, and no login rate
  limiting. The hotel user stories require both. It also covers the limits of the hotel search (availability only,
  even split of guests per room).
- Public endpoints need a `permitAll` matcher in `SecurityConfig` and an empty `@SecurityRequirements` on the
  operation. Admin endpoints live under `/api/admin/**` with a class-level `@PreAuthorize("hasRole('ADMIN')")`.
