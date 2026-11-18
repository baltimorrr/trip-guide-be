# trip-guide

A copyable starting point for REST APIs with Spring Boot and PostgreSQL. It has user registration and JWT login,
role-based access, consistent JSON errors, database migrations, API docs, and tests against a real database.
One sample feature (`product`) shows the conventions end to end.

It also holds the first features of a hotel booking platform:

- **Accounts:** sign-up with email verification, JWT sign-in with a session per device, lockout, password reset,
  profile.
- **Search:** location autocomplete, priced hotel search with filters, filter counts and sorting, popular
  destinations, and admin curation of destinations.
- **Hotel page:** overview, facilities, policies, rooms and rates with cancellation penalties, reviews.
- **Wishlist and cart:** saved hotels; a cart priced live, which merges a guest's browser cart on sign-in.
- **Checkout:** a quote with discount code and taxes, then a booking that holds its rooms for 15 minutes.
- **Payment:** card payment with Stripe, confirmed only by Stripe's signed webhook; the booking page and emails;
  cancellation with a refund by the rate's policy.

How each one works, and every endpoint: [docs/features.md](docs/features.md).

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

**Port already in use?** Set `SERVER_PORT` (app) or `POSTGRES_PORT` (database) in `.env`. Every setting is listed
in [docs/development.md](docs/development.md#configuration).

The database keeps running after the app stops, so restarts are fast. Stop it with `docker compose stop`.
`docker compose down -v` deletes it with all its data.

The app refuses to start if `APP_JWT_SECRET` is missing or shorter than 32 bytes, and the error says how to fix it.

## Run with Docker

Only Docker with Compose v2 is needed, no JDK: the jar is built inside Docker.

```bash
cp .env.example .env
docker run --rm alpine sh -c 'head -c 32 /dev/urandom | base64'   # paste the output into APP_JWT_SECRET in .env
docker compose --profile app up --build
```

The first build downloads Maven and every dependency, so it can take 10–15 minutes; later builds reuse them. The app
then answers at the URLs above (port `SERVER_PORT` from `.env`, 8080 by default), with the demo hotels loaded.

- Logs print in this terminal, and Ctrl+C stops the app and the database. Emailed links (verify email, reset
  password) are only written to these logs.
- Without the Stripe keys in `.env`, the payment endpoints answer 503. Everything else works.
- After a code change, run the same command again: `--build` rebuilds the image.
- `docker compose --profile app down` removes the containers. Add `-v` to delete the database too.

## Try it in Swagger

Under the `local` profile, every form in Swagger UI (`/swagger-ui.html`) opens filled in with values that work on
the demo data, so most calls need only **Execute**.

1. `POST /api/auth/login`: pick **Demo customer** or **Demo admin** in the example list, then Execute.
2. Paste the response's `accessToken` into **Authorize** (top right). It lasts 15 minutes, so sign in again when calls
   answer 401. The page keeps it across reloads.
3. Book a room in this order: `GET /api/search/hotels` (Đà Nẵng) → `GET /api/hotels/{slug}/rooms` (Mỹ Khê) →
   `POST /api/cart/items` → `POST /api/bookings/quote` (code `welcome10`) → `POST /api/bookings`. The dates are a
   2-night stay 30 days ahead.

Some values can only come from an earlier response:

- `POST /api/bookings`: replace `expectedTotal` with the quote's `price.total`. Any other total answers 409.
- The examples use rate plan `9` and cart line `1`, which are right on a new database. Otherwise copy `ratePlanId`
  from the rooms and the line `id` from `GET /api/cart`.
- `{reference}` is the booking's `reference`. Verify-email and reset-password tokens are in the links in the app log.
- Without Stripe keys, `POST /api/bookings/{reference}/payment` answers 503, so a booking stays Pending and can't be
  cancelled.

## Tests

```bash
./mvnw verify      # needs Docker running; starts a throwaway PostgreSQL 18 container
```

What each test class covers: [docs/development.md](docs/development.md#tests).

## Documentation

| File | Contents |
|------|----------|
| [docs/features.md](docs/features.md) | endpoints, errors, rate limits, each feature's rules, curl examples, known limitations |
| [docs/development.md](docs/development.md) | project structure, conventions, adding a feature, tests, every setting, production notes, using this as a template |
| [USER_STORIES.md](USER_STORIES.md) | the platform's user stories; codes like C-PAY-01 in the docs refer to them |
| [DATABASE-SCHEMA.md](DATABASE-SCHEMA.md) | the platform's target database schema |
| [CLAUDE.md](CLAUDE.md) | maintainer notes for Claude Code: architecture, rules, decisions |
