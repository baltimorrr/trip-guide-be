---
phase: 2
title: Destination sync
status: completed
priority: P1
effort: 2.5h
dependencies:
  - 1
---

# Phase 2: Destination sync

## Overview

Build `destinations` from active hotels' addresses (country, city, district, ward), keep `hotel_count` current, and
never touch admin-owned columns. Runs on startup, on a cron, and on demand (admin endpoint in phase 4).

## Context

- `DATABASE-SCHEMA.md` → `destinations` note (sync copies places of active hotels, refreshes `hotel_count`, never
  overwrites image / featured order / centre / display name).
- Phase 1 tables, `norm()`, `Destination` entity.

## Requirements

- Functional: one row per place used by an ACTIVE, non-deleted hotel; `hotel_count` = number of such hotels inside;
  places that lose all hotels keep their row with `hotel_count = 0` (hidden from autocomplete by the index filter).
- Functional: country rows get an English display name (`Locale.of("", "VN").getDisplayCountry(Locale.ENGLISH)`).
- Non-functional: idempotent; safe when two app instances run it at once; admin edits survive every run.

## Architecture

- `hotel/repository/HotelSql` — shared SQL fragments so sync counts, search and popular use **one** location rule:
  ```java
  /** Hotel address {@code a} lies inside destination {@code d}. Same expressions as the addresses_place_match index. */
  public static final String ADDRESS_IN_DESTINATION = """
          a.country_code = d.country_code
          and (d.type = 'COUNTRY' or (
              coalesce(norm(a.state_province), '') = d.province_key
              and coalesce(norm(a.city), '') = d.city_key
              and (d.type = 'CITY' or (
                  coalesce(norm(a.district), '') = d.district_key
                  and (d.type = 'DISTRICT' or coalesce(norm(a.ward), '') = d.ward_key)))))""";
  /** Hotel {@code h} is visible to travellers. */
  public static final String HOTEL_LISTED = "h.status = 'ACTIVE' and h.deleted_at is null";
  /** Cover photo of hotel {@code h}, falling back to its first hotel photo; used by search and popular. */
  public static final String COVER_PHOTO_URL = """
          (select p.url from hotel_photos p where p.hotel_id = h.id and p.room_type_id is null
           order by p.is_cover desc, p.sort_order, p.id limit 1)""";
  ```
- `DestinationSyncRepository` (JdbcClient) runs, inside one transaction owned by the service:
  1. `select pg_advisory_xact_lock(hashtext('destination_sync'))` — serializes concurrent runs.
  2. **Insert new places** (`returns` inserted count):
     ```sql
     with hotel_places as (
         select a.country_code,
                coalesce(norm(a.state_province), '') as province_key, coalesce(norm(a.city), '') as city_key,
                coalesce(norm(a.district), '') as district_key,     coalesce(norm(a.ward), '') as ward_key,
                btrim(a.city) as city, btrim(a.district) as district, btrim(a.ward) as ward
         from hotels h join addresses a on a.id = h.address_id
         where h.status = 'ACTIVE' and h.deleted_at is null),
     places as (
         select 'COUNTRY' as type, country_code, '' as province_key, '' as city_key, '' as district_key,
                '' as ward_key, country_code as name
         from hotel_places group by country_code
         union all
         select 'CITY', country_code, province_key, city_key, '', '', mode() within group (order by city)
         from hotel_places group by country_code, province_key, city_key
         union all
         select 'DISTRICT', country_code, province_key, city_key, district_key, '', mode() within group (order by district)
         from hotel_places where district_key <> '' group by country_code, province_key, city_key, district_key
         union all
         select 'WARD', country_code, province_key, city_key, district_key, ward_key, mode() within group (order by ward)
         from hotel_places where ward_key <> ''
         group by country_code, province_key, city_key, district_key, ward_key),
     slugged as (
         select p.*, rtrim(lower(p.country_code) || '-' || btrim(regexp_replace(
                    concat_ws(' ', nullif(p.city_key, ''), nullif(p.district_key, ''), nullif(p.ward_key, '')),
                    '[^a-z0-9]+', '-', 'g'), '-'), '-') as base_slug
         from places p)
     insert into destinations (type, name, slug, country_code, province_key, city_key, district_key, ward_key,
                               source, created_at, updated_at)
     select type, name,
            case when count(*) over (partition by base_slug) > 1
                      or exists (select 1 from destinations x where x.slug = s.base_slug)
                 then base_slug || '-' || left(md5(concat_ws('|', country_code, province_key, city_key,
                                                             district_key, ward_key)), 6)
                 else base_slug end,
            country_code, province_key, city_key, district_key, ward_key, 'SYNC', now(), now()
     from slugged s
     on conflict (country_code, province_key, city_key, district_key, ward_key) where deleted_at is null do nothing
     ```
     Slugs: `vn`, `vn-da-nang`, `vn-da-nang-hai-chau`, `vn-da-nang-hai-chau-thach-thang`; a 6-char key hash is
     appended only on collision (e.g. same city name in two provinces). `mode()` picks the most common spelling.
  3. **Refresh counts** (all non-deleted rows; sync never sets `updated_at`, which stays "last admin edit"):
     ```sql
     update destinations d
     set hotel_count = (select count(*) from hotels h join addresses a on a.id = h.address_id
                        where <HOTEL_LISTED> and <ADDRESS_IN_DESTINATION>),
         last_synced_at = now()
     where d.deleted_at is null
     ```
  4. **Country names**: select COUNTRY rows whose `name = country_code`; for each, if `Locale` returns a different
     non-blank English name, `update destinations set name = :name where id = :id` (normalized_name follows).
- `DestinationSyncService.sync()` → `DestinationSyncResult(int created, int withHotels)` (`withHotels` = count of
  non-deleted rows with `hotel_count > 0`). Logs one INFO line.
- `DestinationSyncJob` (`destination/sync/`): `@Scheduled(cron = "${app.destinations.sync.cron}")` + an
  `@EventListener(ApplicationReadyEvent.class)` that runs once when `app.destinations.sync.on-startup` is true.
  Catches and logs exceptions so a failed run never kills the scheduler thread or startup.
- `DestinationSyncProperties` record `@ConfigurationProperties("app.destinations.sync")`: `cron`, `onStartup`.
- `common/config/SchedulingConfig` with `@EnableScheduling`.

## Related Code Files

- Create: `spring-base/src/main/java/com/company/project/hotel/repository/HotelSql.java`
- Create: `spring-base/src/main/java/com/company/project/destination/repository/DestinationSyncRepository.java`
- Create: `spring-base/src/main/java/com/company/project/destination/services/DestinationSyncService.java`
- Create: `spring-base/src/main/java/com/company/project/destination/services/impl/DestinationSyncServiceImpl.java`
- Create: `spring-base/src/main/java/com/company/project/destination/dto/DestinationSyncResult.java`
- Create: `spring-base/src/main/java/com/company/project/destination/sync/DestinationSyncJob.java`
- Create: `spring-base/src/main/java/com/company/project/destination/sync/DestinationSyncProperties.java`
- Create: `spring-base/src/main/java/com/company/project/common/config/SchedulingConfig.java`
- Modify: `spring-base/src/main/resources/application.yml` (`app.destinations.sync.cron: "0 */10 * * * *"`, `on-startup: true`)
- Modify: `spring-base/src/test/resources/application-test.yml` (`cron: "-"`, `on-startup: false`)
- Create: `spring-base/src/test/java/com/company/project/destination/services/impl/DestinationSyncServiceImplTest.java`
  (`@IntegrationTest`; the logic is SQL, so a Mockito test would prove nothing)

## Implementation Steps

1. `HotelSql` with `ADDRESS_IN_DESTINATION`, `HOTEL_LISTED` and `COVER_PHOTO_URL` (final class, private constructor, javadoc on the
   alias contract: `h` = hotels, `a` = addresses, `d` = destinations).
2. `DestinationSyncRepository` methods: `lock()`, `insertNewPlaces()`, `refreshHotelCounts()`,
   `countriesNamedByCode()` → `List<CountryRow(id, countryCode)>`, `rename(id, name)`, `countWithHotels()`.
3. `DestinationSyncServiceImpl.sync()` `@Transactional`: lock → insert → refresh → rename countries → result.
4. Properties, job, scheduling config, yml entries.
5. Tests (each builds its own hotels with unique place names via `CatalogFixtures`, then calls `sync()`):
   - ACTIVE hotel in `Đà Nẵng x1 / Hải Châu x1 / Thạch Thang x1` → CITY, DISTRICT, WARD rows exist with
     `hotel_count = 1`, slugs as specified, `normalized_name` accent-free; COUNTRY row for its country exists.
   - DRAFT/INACTIVE/deleted hotel → no new place rows for its unique city.
   - two hotels whose city differs only by accents/case/spaces (`"Đà Nẵng x2"`, `" da  nang X2"`) → one CITY row,
     `hotel_count = 2`.
   - hotel set INACTIVE after a sync → next sync sets `hotel_count = 0`, row kept.
   - admin-owned fields: set `name`, `image_url`, `featured_rank` via SQL → re-sync leaves them unchanged.
   - running `sync()` twice creates nothing the second time (`created == 0` for the test's places is checked by
     row count on its keys, not the global result).
   - same city name in two provinces (unique suffix) → two CITY rows, distinct slugs.
   - country names: use a country code no other test uses with a known English name (e.g. `IS` → "Iceland").
6. Run `./mvnw test -Dtest=DestinationSyncServiceImplTest`.

## Success Criteria

- [x] All sync tests pass; existing suite green.
- [ ] Sync with no hotels at all is a no-op (startup on an empty DB logs `created=0`).
- [x] Test profile never schedules the job (`cron: "-"`).

## Risk Assessment

- **Count query cost**: correlated `count(*)` per destination; fine for thousands of rows every 10 min. If it grows,
  replace with one grouped count (`grouping sets`) joined back by level.
- **Soft-deleted destinations** are re-created by the sync (unique index ignores deleted rows). Not reachable now
  (no delete endpoint); A-CONT-01 should hide places with a flag instead of `deleted_at`.
- **Locale names** depend on the JDK's CLDR data; admin can rename (phase 4).
