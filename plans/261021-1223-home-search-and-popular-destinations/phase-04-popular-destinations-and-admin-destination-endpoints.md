---
phase: 4
title: Popular destinations and admin destination endpoints
status: completed
priority: P1
effort: 3h
dependencies:
  - 2
---

# Phase 4: Popular destinations and admin destination endpoints

## Overview

C-HOME-03 homepage section (featured destinations with image and a few top hotels each), a public lookup by slug
used to rebuild a shared or stored search, and the ADMIN slice of A-CONT-01 needed to curate featured destinations.

## Context

- `USER_STORIES.md` C-HOME-03, A-CONT-01.
- `Destination` entity + `updateDisplay` (phase 1), `DestinationSyncService` (phase 2), `HotelSql` (phase 2).
- `product/` feature for controller/mapper/service idioms; `PageResponse.from(page)` for lists.

## Requirements

**Public**
- `GET /api/destinations/popular?limit=8&hotels=4` → `List<PopularDestinationResponse(slug, type, name, imageUrl,
  hotelCount, List<PopularHotel> hotels)>`; `PopularHotel(id, slug, name, starRating, coverPhotoUrl, userRatingAvg,
  userRatingCount, city)`. `limit` clamped 1..20, `hotels` clamped 1..10 (clamping matches the page-size cap style).
  - Destinations: non-deleted, `hotel_count > 0`, `featured_rank is not null`, order `featured_rank, name`.
    If none: top CITY destinations by `hotel_count desc, name`.
  - Hotels per destination: `HOTEL_LISTED` and `ADDRESS_IN_DESTINATION`, order `user_rating_avg desc nulls last,
    google_rating desc nulls last, id`, limit `hotels`.
- `GET /api/destinations/{slug}` → `DestinationResponse(slug, type, name, countryCode, hotelCount, imageUrl)`;
  404 when missing or deleted. Lets a client turn `?destination=vn-da-nang` from a shared URL or stored last search
  back into a label, and detect a stale stored slug.
- The "click runs a search" rule stays client-side (the client knows the traveller's "tomorrow"):
  `/api/search/hotels?destination={slug}&checkIn={tomorrow}&checkOut={tomorrow+1}&rooms=1&adults=2`. Documented in
  README; an integration test calls exactly this URL.

**ADMIN** (`/api/admin/destinations`, class-level `@PreAuthorize("hasRole('ADMIN')")`)
- `GET ?q=&page=&size=` → `PageResponse<AdminDestinationResponse(id, slug, type, name, countryCode, hotelCount,
  imageUrl, featuredRank, source, lastSyncedAt, updatedAt)>`; non-deleted rows whose `normalized_name` starts with
  `norm(q)` (`q` defaults to `""` = all); fixed order `featured_rank nulls last, hotel_count desc, name, id`; sort
  param ignored.
- `PUT /{id}` body `DestinationUpdateRequest(@NotBlank @Size(max = 150) name, @Size(max = 500)
  @Pattern(regexp = "^https?://\\S+$") imageUrl, @Min(1) @Max(999) Short featuredRank)` → full replace of the three
  admin fields (`featuredRank: null` = not featured). 404 for missing/deleted id.
- `POST /sync` → runs `DestinationSyncService.sync()` now, returns `DestinationSyncResult`.

## Related Code Files

- Create: `spring-base/src/main/java/com/company/project/destination/controller/DestinationController.java`
- Create: `spring-base/src/main/java/com/company/project/destination/controller/AdminDestinationController.java`
- Create: `spring-base/src/main/java/com/company/project/destination/dto/` `PopularDestinationResponse`,
  `PopularHotel`, `DestinationResponse`, `AdminDestinationResponse`, `DestinationUpdateRequest`
- Create: `spring-base/src/main/java/com/company/project/destination/mapper/DestinationMapper.java`
- Create: `spring-base/src/main/java/com/company/project/destination/services/DestinationService.java`
- Create: `spring-base/src/main/java/com/company/project/destination/services/impl/DestinationServiceImpl.java`
- Create: `spring-base/src/main/java/com/company/project/destination/repository/DestinationQueryRepository.java` (JdbcClient)
- Modify: `spring-base/src/main/java/com/company/project/destination/repository/DestinationRepository.java`
  (native `searchByNamePrefix(String q, Pageable)` with `countQuery`, fixed `order by`; call it with an unsorted
  `PageRequest.of(page, size)`)
- Modify: `spring-base/src/main/java/com/company/project/common/config/SecurityConfig.java`
  (`.requestMatchers(HttpMethod.GET, "/api/destinations/**").permitAll()`; admin paths stay authenticated)
- Create: `spring-base/src/test/java/com/company/project/destination/controller/DestinationControllerTest.java`
- Create: `spring-base/src/test/java/com/company/project/destination/controller/AdminDestinationControllerTest.java`
- Create: `spring-base/src/test/java/com/company/project/destination/services/impl/DestinationServiceImplTest.java` (Mockito)

## Implementation Steps

1. `DestinationQueryRepository`:
   - `featuredIds(limit)` and `topCityIds(limit)` → `List<Long>`.
   - `topHotels(List<Long> destinationIds, int perDestination)` → flat rows `(destinationId, PopularHotel fields)`:
     ```sql
     select d.id as destination_id, x.*
     from destinations d
     join lateral (
         select h.id, h.slug, h.name, h.star_rating, h.user_rating_avg, h.user_rating_count, a.city,
                <COVER_PHOTO_URL> as cover_photo_url
         from hotels h join addresses a on a.id = h.address_id
         where <HOTEL_LISTED> and <ADDRESS_IN_DESTINATION>
         order by h.user_rating_avg desc nulls last, h.google_rating desc nulls last, h.id
         limit :perDestination) x on true
     where d.id in (:ids)
     ```
2. `DestinationServiceImpl.popular(limit, hotels)`: ids = featured or fallback → `destinations.findAllById(ids)`
   reordered by `ids` → group hotel rows by destination id (`LinkedHashMap`) → mapper builds responses. Empty ids →
   empty list without querying hotels.
3. `findBySlug`, `adminSearch(q, pageable)`, `update(id, request)` (`load` → `updateDisplay` → `saveAndFlush` →
   mapper), and `DestinationMapper` (`toResponse`, `toAdminResponse`, `toPopular`).
4. Controllers: public one with `@SecurityRequirements` on both operations; admin one at class level
   `@PreAuthorize`. Admin `PUT` uses `@Valid @RequestBody`.
5. SecurityConfig matcher.
6. Tests:
   - `DestinationServiceImplTest` (Mockito): fallback to top cities when nothing is featured; no hotel query for an
     empty id list; ordering follows ids.
   - `DestinationControllerTest`: build two cities with hotels (unique names), sync, set `featured_rank` 1 and 2 and
     an image via the admin endpoint; `popular?limit=20&hotels=2` contains both in rank order (assert relative order
     of the test's own slugs only — the DB is shared), each with ≤ 2 hotels ordered by rating, INACTIVE hotel absent,
     image returned; the documented default search URL for one of them returns its hotel; `GET /{slug}` 200, unknown 404;
     no token needed.
   - `AdminDestinationControllerTest`: anonymous 401, USER 403; ADMIN list with `q` finds the test's city; PUT sets
     name/image/rank and the new name is found by `/api/search/suggestions` (generated `normalized_name`); PUT
     validation (blank name, `ftp://` image, rank 0) → 400; PUT unknown id → 404; `POST /sync` → 200 and a newly
     inserted hotel's city appears; a later sync keeps the admin's name/image/rank.
7. Run `./mvnw test -Dtest='Destination*Test,AdminDestinationControllerTest'`.

## Success Criteria

- [x] All tests above pass; existing suite green.
- [x] Admin updates never change `hotel_count`, keys, slug or `last_synced_at` (entity `updatable = false` columns).
- [ ] Public endpoints appear without a lock icon in Swagger; admin ones with it.

## Risk Assessment

- **Shared test DB + global "featured" list**: other tests may feature rows too; tests assert relative order of their
  own rows with `limit=20`, never absolute positions.
- **Stale admin edits**: clients don't send `version` (existing known limitation); last write wins for name/image/rank.
- **Image URLs** are stored, not fetched or proxied; `^https?://` pattern blocks `javascript:` style values.
