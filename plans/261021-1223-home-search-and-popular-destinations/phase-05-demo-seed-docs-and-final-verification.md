---
phase: 5
title: 'Demo seed, docs and final verification'
status: completed
priority: P2
effort: 2h
dependencies:
  - 3
  - 4
---

# Phase 5: Demo seed, docs and final verification

## Overview

Local-only demo catalog so the homepage endpoints return data in dev, README/CLAUDE.md updates for the new
conventions and API (including the C-HOME-02 client contract), then full verification.

## Context

- `spring-base/README.md` (Endpoints, Tests, Project structure, Configuration, Known limitations),
  `spring-base/CLAUDE.md` (conventions paragraph that currently says "ask the user which conventions win").
- Memory: local `.env` uses `SERVER_PORT=8081`, `POSTGRES_PORT=5434`; never stop other containers.

## Requirements

- Seed loads only in the `local` profile; `test` and `prod` never see it.
- Seed is idempotent (Flyway repeatable migration re-runs when its checksum changes).
- Docs describe only what was built; dates and claims match the code.

## Related Code Files

- Create: `spring-base/src/main/resources/db/seed/R__demo_catalog.sql`
- Modify: `spring-base/src/main/resources/application-local.yml`
  (`spring.flyway.locations: classpath:db/migration,classpath:db/seed`)
- Modify: `spring-base/README.md`
- Modify: `spring-base/CLAUDE.md`

## Implementation Steps

1. **Seed** (`R__demo_catalog.sql`, fictional names, no real brands):
   - Partner `demo-partner@example.com` (APPROVED) inserted `where not exists`.
   - ~8 ACTIVE hotels: Đà Nẵng (Hải Châu ×2, Sơn Trà ×2), Hồ Chí Minh (Quận 1 ×2), Hà Nội (Hoàn Kiếm ×1),
     Hội An (Quảng Nam ×1), plus 1 INACTIVE hotel in Huế (shows "no active hotel → not suggested"). Each hotel:
     address with accented names, 2 room types (e.g. Deluxe Double 2/0/2, Family 2/2/4), cover photo
     `https://picsum.photos/seed/<slug>/800/600`, ratings. A few `room_inventory` rows (one closed night, one sold-out
     night) to demo availability.
   - Each hotel block keyed by slug: `with addr as (insert into addresses ... select ... where not exists
     (select 1 from hotels where slug = '...') returning id) insert into hotels ... select ... from addr;` and child
     rows guarded the same way.
   - Featured CITY destinations pre-inserted with keys computed by `norm()` on the same literals as the addresses,
     `featured_rank` 1..4 and an image, `on conflict (country_code, province_key, city_key, district_key, ward_key)
     where deleted_at is null do nothing`. The startup sync then fills counts and the other levels without touching
     them.
   - Header comment: local profile only; reset with `docker compose down -v`.
2. **README**:
   - Endpoints table: the 7 new rows (plan.md "API Added").
   - New section "Search and destinations": criteria rules and limits (`app.search.*`), capacity rule, sync
     (startup + `app.destinations.sync.cron`, `"-"` disables), popular fallback, default-search URL for a clicked
     destination, and **C-HOME-02 client contract**: the browser saves the last search's query parameters plus the
     chosen suggestion's label (e.g. `localStorage["lastSearch"] = {destination:{slug,name}|hotel:{slug,name},
     checkIn, checkOut, rooms, adults, childAges}`), drops the dates if check-in has passed, checks a stored
     destination with `GET /api/destinations/{slug}` (404 → forget it); the server stores nothing.
   - Configuration table: `APP_DESTINATIONS_SYNC_CRON`, `APP_DESTINATIONS_SYNC_ON_STARTUP`, `APP_SEARCH_*` defaults.
   - Tests table: new test classes and what they cover. Project structure: `destination/`, `search/`,
     `hotel/repository/HotelSql`, `db/seed/`, `support/CatalogFixtures`.
   - Production notes: the DB role needs CREATE on the database for `unaccent`/`pg_trgm` (trusted extensions).
   - Known limitations: availability only (no prices/rate plans), even guest split per room, `max_children` rule,
     sort ignored, invalid token on public endpoints → 401.
3. **CLAUDE.md**: replace the "ask the user which conventions win" sentence with the decision (base conventions +
   schema-doc names; composite-key tables keep schema keys; varchar instead of enums/char/citext; FKs added with the
   referenced table). Add: `norm()` is the only normalizer; SQL-heavy reads use `JdbcClient` repositories; shared
   location SQL lives in `HotelSql`; integration tests build catalog data with `CatalogFixtures` and unique names;
   sync is disabled in the test profile and called directly.
4. **Verify**:
   - `cd spring-base && ./mvnw verify` (Docker running) — all tests green.
   - Local smoke: `./mvnw spring-boot:run` (port 8081 from `.env`), wait for the startup sync log line, then curl:
     `/api/search/suggestions?q=da%20nang`, `/api/search/hotels?destination=vn-da-nang&checkIn=<+7d>&checkOut=<+8d>&rooms=1&adults=2`,
     `/api/destinations/popular`, `/api/destinations/vn-da-nang`; login as the bootstrap admin from `.env` and call
     `GET /api/admin/destinations` and `POST /api/admin/destinations/sync`. Stop the app with
     `pkill -f "[c]om.company.project.ProjectApplication"`; leave Postgres and other containers running.
   - Re-read README/CLAUDE.md sections against the code (paths, property names, defaults).
5. Report: changes are uncommitted (user commits); list any skipped step with the reason.

## Success Criteria

- [x] `./mvnw verify` passes.
- [x] Local smoke calls return seeded data; the INACTIVE Huế hotel's city is not suggested.
- [x] Seed absent from test and prod (no `db/seed` in their Flyway locations).
- [x] README and CLAUDE.md match the implemented behavior.

## Risk Assessment

- **Seed vs existing local DB**: V1/V2 already applied there; V3 + `R__` apply on next start. If a seed edit breaks
  idempotency, `docker compose down -v` resets local data (dev only, says so in README).
- **picsum.photos** is an external placeholder service; only URLs are stored, nothing is fetched server-side.
- **Seed jar size**: the seed file ships inside the jar but only runs when its location is configured (local).
