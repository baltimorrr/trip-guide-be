# spring-base

A copyable starting point for REST APIs with Spring Boot and PostgreSQL. It has user registration and JWT login,
role-based access, consistent JSON errors, database migrations, API docs, and tests against a real database.
One sample feature (`product`) shows the conventions end to end.

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

# register (role USER)
curl -s -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"jane-password-1","fullName":"Jane Doe"}' \
  $BASE/api/auth/register

# login → access token
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"jane-password-1"}' \
  $BASE/api/auth/login | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s -H "Authorization: Bearer $TOKEN" $BASE/api/auth/me
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/products?page=0&size=20&sort=name,asc"

# creating products needs ADMIN: log in as APP_BOOTSTRAP_ADMIN_EMAIL instead, then
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Coffee beans","description":"1kg bag","price":19.99}' $BASE/api/products
```

## Endpoints

| Method | Path | Access | Notes |
|--------|------|--------|-------|
| POST | `/api/auth/register` | public | 201; email is stored lowercased; 409 if taken |
| POST | `/api/auth/login` | public | 200 `{accessToken, tokenType, expiresIn}`; 401 on any failure |
| GET | `/api/auth/me` | authenticated | current user |
| GET | `/api/products` | authenticated | paged: `{content: [...], page: {size, number, totalElements, totalPages}}`; max size 100 |
| GET | `/api/products/{id}` | authenticated | 404 if missing |
| POST | `/api/products` | ADMIN | 201 + `Location` |
| PUT | `/api/products/{id}` | ADMIN | |
| DELETE | `/api/products/{id}` | ADMIN | 204 |
| GET | `/actuator/health`, `/actuator/info` | public | |
| GET | `/swagger-ui.html`, `/v3/api-docs` | public | disabled in the `prod` profile |

Every error is `application/problem+json` (RFC 9457):

```json
{"title": "Bad Request", "status": 400, "detail": "Validation failed", "instance": "/api/products",
 "errors": [{"field": "price", "message": "must be greater than or equal to 0.00"}]}
```

## Tests

```bash
./mvnw verify      # needs Docker running; starts a throwaway PostgreSQL 18 container
```

| Test | Kind | Covers |
|------|------|--------|
| `ProjectApplicationTest` | full app | context starts, health is UP |
| `ErrorResponseTest` | full app | problem+json for unknown paths, API docs served |
| `AuthControllerTest` | full app | register/login/me, validation, 72-byte password limit, bad/tampered tokens, admin bootstrap |
| `ProductControllerTest` | full app | CRUD, USER vs ADMIN (403), 404, validation, sort/page limits, write-response timestamps equal stored ones |
| `UserRepositoryTest` | JPA slice | mapping, roles table, unique email, audit timestamps |
| `TokenServiceTest` | unit | claims, wrong key, expired, wrong issuer, weak secret |
| `UserServiceImplTest` | unit | 72-byte password limit, duplicate email (409), normalized email and hashed password |
| `ProductServiceImplTest` | unit | 404 for a missing id, create/update go through the mapper |
| `AdminBootstrapTest` | unit | a concurrently created admin is skipped, any other error still fails startup |

Each test sits in the package of the class it covers (e.g. `product/controller/ProductControllerTest`).
Full-app tests use `@IntegrationTest` (`src/test/.../support`). Every class shares one Spring context and one
container, so the suite stays fast. Tests create their own data with unique emails and never assume an empty database.
Unit tests use Mockito and start no Spring context.

`TestProjectApplication` (in `src/test`) runs the app against a Testcontainers database instead of Docker Compose.

## Project structure

```text
src/main/java/com/company/project/
├── ProjectApplication.java
├── common/                              shared by every feature; imports no feature package
│   ├── config/                          SecurityConfig, OpenApiConfig (API docs), JpaAuditingConfig
│   ├── dto/PageResponse.java            paged list body: {content, page}
│   ├── exception/                       GlobalExceptionHandler, BusinessException, ResourceNotFoundException,
│   │                                    ProblemDetailSecurityHandler (401/403 → problem+json)
│   └── model/BaseEntity.java            id, version, createdAt, updatedAt
├── auth/                                register / login / me
│   ├── controller/  dto/  services/ (+ impl/)
│   └── security/                        JWT encode/decode, token issuing, user lookup for login
├── user/                                User entity, roles, account creation
│   ├── model/  repository/  dto/  mapper/  services/ (+ impl/)
│   └── bootstrap/                       first admin account on startup
└── product/                             sample feature: copy this shape
    └── controller/  model/  repository/  dto/  mapper/  services/ (+ impl/)
src/main/resources/
├── application.yml                      defaults for every profile
├── application-local.yml                local dev (the default profile): .env, Docker Compose
├── application-prod.yml                 production overrides
└── db/migration/                        V1__create_user_tables.sql, V2__create_product_tables.sql
```

Code is grouped **by feature, then by layer**:

- `controller/`: REST endpoints. Depends on the service interface, never on `impl/`.
- `services/`: the service interface (the contract). `services/impl/` holds the `@Service` implementation and its
  transactions.
- `repository/`: Spring Data repositories. `model/`: JPA entities and enums.
- `dto/`: request and response records. `mapper/`: `@Component` classes that convert between DTOs and entities.

A folder only exists when a feature has something in it. For example, `user/` has no controller because its
endpoints live in `auth/`.

## Adding a feature (checklist)

1. **Migration**: add `src/main/resources/db/migration/V{next}__create_thing_tables.sql` with the standard columns
   (`id bigint generated by default as identity primary key`, `version bigint not null default 0`,
   `created_at timestamptz not null`, `updated_at timestamptz not null`).
2. **Entity**: `thing/model/Thing.java extends BaseEntity`, with `@Getter` and `@NoArgsConstructor(access = PROTECTED)`.
3. **Repository**: `thing/repository/ThingRepository extends JpaRepository<Thing, Long>`.
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
| `SERVER_PORT` | 8080 | HTTP port |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_PORT` | app / app / required / 5432 | local Compose database |

Token lifetime is `app.jwt.access-token-ttl` (default `1h`) in `application.yml`.

## Production notes

- Run with `SPRING_PROFILES_ACTIVE=prod`. This turns off Swagger UI and `/v3/api-docs`.
- Provide the database with `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
  Docker Compose support is dev-only and is not included in the packaged jar.
- Build a jar with `./mvnw package` (`target/spring-base-0.0.1-SNAPSHOT.jar`), or a container image with
  `./mvnw spring-boot:build-image`.
- Provide `APP_JWT_SECRET` from a secret manager. Changing it invalidates every issued token.

## Using this as a template

1. Copy the folder **under a new folder name** and delete `target/`, `.env`, and `.git/`.
   Docker Compose names the database container and volume after the folder. Two copies both called
   `spring-base` would share one database.
2. Rename `groupId`/`artifactId`/`name` in `pom.xml`, the `com.company.project` package (and `ProjectApplication`
   if you like), and `spring.application.name`.
3. Delete or replace the `product` feature and `V2__create_product_tables.sql`. If the copy is brand new and has never
   run, you may renumber migrations.
4. `git init`.

## Known limitations (deliberate, for a base project)

- **No refresh tokens or revocation.** A stolen access token works until it expires, and so does the token of a
  disabled user. Keep `access-token-ttl` short, and add refresh tokens when you need long sessions.
- **No rate limiting on login.** Add one (e.g. Bucket4j or at the gateway) before exposing the API publicly.
- **Registration reveals whether an email exists** (409). Login does not.
- **Optimistic locking only guards concurrent transactions.** Clients don't send `version`, so a stale edit
  from a client can still overwrite newer data.
