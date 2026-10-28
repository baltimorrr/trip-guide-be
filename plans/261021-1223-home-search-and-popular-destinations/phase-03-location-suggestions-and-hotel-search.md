---
phase: 3
title: Location suggestions and hotel search
status: completed
priority: P1
effort: 4h
dependencies:
  - 2
---

# Phase 3: Location suggestions and hotel search

## Overview

Public C-HOME-01 endpoints: accent-insensitive location autocomplete, and an availability search driven only by GET
query parameters, which is also the C-HOME-02 contract (the browser stores and replays the same parameters).

## Context

- `USER_STORIES.md` C-HOME-01, C-HOME-02.
- `HotelSql` (phase 2), `DestinationRepository.findBySlugAndDeletedAtIsNull` (phase 1).
- `common/config/SecurityConfig.java`, `auth/controller/AuthController.java` (empty `@SecurityRequirements` for public ops),
  `common/exception/GlobalExceptionHandler.java`.

## Requirements

**Suggestions** `GET /api/search/suggestions?q=da%20nang&limit=5`
- `q` trimmed length 2..100 else 400; `limit` per group, default 5, clamped to 1..10.
- Response `{"destinations": [...], "hotels": [...]}`:
  - `DestinationSuggestion(slug, type, name, context, hotelCount)` — `context` = parent names, e.g. ward
    "Thạch Thang" → "Hải Châu, Đà Nẵng, Vietnam"; country → null.
  - `HotelSuggestion(slug, name, context)` — `context` = `concat_ws(', ', a.district, a.city)`.
- Match = word prefix on the normalized text: `x like p || '%' or x like '% ' || p || '%'`, `p = norm(:q)`; the Java
  side escapes `\`, `%`, `_` in `q` first (norm leaves them untouched), so user input is never a wildcard.
- Destinations: `deleted_at is null and hotel_count > 0` (same predicate as the GIN index). Order: exact match,
  whole-name prefix, `hotel_count desc`, type (COUNTRY, CITY, DISTRICT, WARD), name.
- Hotels: `HOTEL_LISTED` and `norm(h.name)` (same expression as `hotels_name_search`). Order: whole-name prefix,
  `user_rating_count desc`, name.

**Search** `GET /api/search/hotels?destination=vn-da-nang&checkIn=2026-11-01&checkOut=2026-11-03&rooms=1&adults=2&childAges=4&childAges=9&page=0&size=20`
- Location: exactly one of `destination` (destination slug) or `hotel` (hotel slug) → else 400. Unknown /
  deleted destination or not-listed hotel → 404 (`ResourceNotFoundException`).
- Bean Validation on `HotelSearchRequest` record: `checkIn`, `checkOut` `@NotNull @DateTimeFormat(iso = DATE)`;
  `rooms`, `adults` `@NotNull @Min(1)`; `List<@NotNull @Min(0) @Max(17) Integer> childAges` (children = list size).
- `SearchCriteria.of(request, today, properties)` (pure, unit-tested) throws `BusinessException(BAD_REQUEST, msg)`:
  check-in before `today`; check-in after `today + maxDaysAhead` (365); check-out ≤ check-in; nights > `maxNights` (30);
  rooms > `maxRooms` (8); adults > `maxAdults` (30); children > `maxChildren` (10); adults < rooms.
  Derives `nights`, `adultsPerRoom = ceil(adults/rooms)`, `childrenPerRoom = ceil(children/rooms)`.
  Service passes `today = LocalDate.now(ZoneOffset.ofHours(-12))` with a comment on why.
- A room type fits when: `ACTIVE`, not deleted, `max_adults >= adultsPerRoom`,
  `max_occupancy >= adultsPerRoom + childrenPerRoom`, and
  `childrenPerRoom <= max_children + (max_adults - adultsPerRoom)` (children may use spare adult places).
- Free rooms of a fitting type = min over nights `[checkIn, checkOut)` of
  `case when coalesce(i.is_closed, false) then 0 else coalesce(i.total_rooms, rt.total_rooms) - coalesce(i.booked_rooms, 0) end`.
  Hotel is available when `sum(greatest(free, 0)) >= rooms`.
- Response: `PageResponse<HotelSearchResult>`; `HotelSearchResult(id, slug, name, accommodationType, starRating,
  coverPhotoUrl, district, city, countryCode, googleRating, googleRatingCount, userRatingAvg, userRatingCount,
  distanceToCenterM)`. Fixed order `user_rating_avg desc nulls last, user_rating_count desc, id` (stable paging);
  `sort` is ignored until C-SRCH-03. Page size cap 100 comes from the global pageable config.

## Architecture

```text
SearchController ──> SearchService (interface) ──> SearchServiceImpl
                                                     ├─ DestinationRepository.findBySlugAndDeletedAtIsNull (JPA)
                                                     ├─ SearchCriteria.of(...)            (pure validation)
                                                     └─ SearchQueryRepository (JdbcClient)
                                                          suggestDestinations / suggestHotels
                                                          findListedHotelId(slug)
                                                          findAvailableHotels / countAvailableHotels
```

Search SQL skeleton (location filter is one of two **constant** fragments, never user text):

```sql
with candidate_hotels as (
    select h.id from hotels h join addresses a on a.id = h.address_id
    /* destination: */ join destinations d on d.id = :destinationId and (<ADDRESS_IN_DESTINATION>)
    where <HOTEL_LISTED> /* hotel: */ and h.id = :hotelId
), nights as (
    select generate_series(:checkIn::date, :checkOut::date - 1, interval '1 day')::date as stay_date
), free_rooms as (
    select rt.hotel_id, rt.id, min(<free expression>) as free
    from room_types rt join candidate_hotels c on c.id = rt.hotel_id
    cross join nights n
    left join room_inventory i on i.room_type_id = rt.id and i.stay_date = n.stay_date
    where rt.status = 'ACTIVE' and rt.deleted_at is null and <fits>
    group by rt.hotel_id, rt.id
), available as (
    select hotel_id from free_rooms group by hotel_id having sum(greatest(free, 0)) >= :rooms
)
select ... from hotels h join available v on v.hotel_id = h.id
join addresses a on a.id = h.address_id join accommodation_types t on t.id = h.accommodation_type_id
order by h.user_rating_avg desc nulls last, h.user_rating_count desc, h.id
limit :limit offset :offset
```

Count query reuses the same CTEs with `select count(*) from available`. `coverPhotoUrl` comes from
`HotelSql.COVER_PHOTO_URL` (phase 2).

## Related Code Files

- Create: `spring-base/src/main/java/com/company/project/search/controller/SearchController.java`
- Create: `spring-base/src/main/java/com/company/project/search/dto/` `HotelSearchRequest`, `HotelSearchResult`,
  `SuggestionsResponse`, `DestinationSuggestion`, `HotelSuggestion`
- Create: `spring-base/src/main/java/com/company/project/search/model/SearchCriteria.java`
- Create: `spring-base/src/main/java/com/company/project/search/config/SearchProperties.java`
  (`@ConfigurationProperties("app.search") @Validated`, defaults in `application.yml`)
- Create: `spring-base/src/main/java/com/company/project/search/services/SearchService.java`
- Create: `spring-base/src/main/java/com/company/project/search/services/impl/SearchServiceImpl.java`
- Create: `spring-base/src/main/java/com/company/project/search/repository/SearchQueryRepository.java`
- Modify: `spring-base/src/main/java/com/company/project/common/config/SecurityConfig.java`
  (`.requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()`)
- Modify: `spring-base/src/main/java/com/company/project/common/exception/GlobalExceptionHandler.java`
- Modify: `spring-base/src/main/resources/application.yml` (`app.search.*` defaults)
- Create: `spring-base/src/test/java/com/company/project/search/model/SearchCriteriaTest.java` (unit)
- Create: `spring-base/src/test/java/com/company/project/search/controller/SearchControllerTest.java` (`@IntegrationTest`)

## Implementation Steps

1. `SearchProperties` record (maxNights 30, maxDaysAhead 365, maxRooms 8, maxAdults 30, maxChildren 10; `@Positive`).
2. `SearchCriteria` record + `of(...)`; `SearchCriteriaTest` covering every rule and the per-room ceilings
   (e.g. 3 adults / 2 rooms → 2 per room).
3. `GlobalExceptionHandler.handleMethodArgumentNotValid`: for `error.isBindingFailure()` (e.g. `checkIn=abc`) use
   the message `"has an invalid value"` so the rejected value never appears (existing rule). Check that a
   `@ModelAttribute` record with `@Valid` raises `MethodArgumentNotValidException` (Spring 7) and is rendered with
   `errors[]`; add a test case.
4. `SearchQueryRepository` with the four queries; record row mappers via `JdbcClient...query(HotelSearchResult.class)`
   or explicit `RowMapper` where column names differ.
5. `SearchServiceImpl` (`@Transactional(readOnly = true)`): suggestions (validate `q`, escape, clamp), search
   (resolve location → criteria → query + count → `PageImpl`).
6. `SearchController`: `@GetMapping("/suggestions")`, `@GetMapping("/hotels")` with
   `@Valid @ParameterObject HotelSearchRequest`, `@ParameterObject @PageableDefault(size = 20) Pageable`; both
   `@SecurityRequirements` (public). Class javadoc states the C-HOME-02 contract: every search is a stateless GET;
   the browser stores these parameters, the server stores nothing.
7. SecurityConfig matcher.
8. `SearchControllerTest` (builds hotels with `CatalogFixtures`, calls the sync service, dates relative to
   `LocalDate.now()` + 30 days so they never fall in the past):
   - no token → 200 on both endpoints.
   - `q=da nang <suffix>` (no accents, lower case) suggests the test's "Đà Nẵng <suffix>" city with context; ward
     context includes district + city; hotel name word-prefix (`q=<second word>`) suggests the hotel.
   - city of an INACTIVE-only hotel is not suggested; `q=%` / `q=_a` return nothing unexpected (escaped);
     `q=a` → 400.
   - search returns an available hotel; excludes: a hotel with a closed night, a fully booked night
     (`booked = total`), a room type too small (3 adults, 1 room, max_adults 2), a DRAFT hotel.
   - multi-room: 2 rooms satisfied by two room types with 1 free each.
   - children: 2 adults + 1 child fits `max_adults 2, max_children 1, max_occupancy 3`; not `max_children 0`.
   - `hotel=<slug>` returns just that hotel; unknown slug → 404; both or neither location → 400.
   - validation: past check-in, check-out = check-in, 31 nights, rooms 9, adults < rooms, `childAges=18`,
     `checkIn=abc` → 400 problem+json without the rejected value.
9. Run `./mvnw test -Dtest='SearchCriteriaTest,SearchControllerTest'`.

## Success Criteria

- [x] All listed cases pass; existing suite green.
- [ ] Swagger shows both operations without a lock icon; params documented via `@ParameterObject`.
- [x] No string concatenation of request values into SQL (only constant fragments + named params).

## Risk Assessment

- **Location predicate and indexes**: the OR-shaped `ADDRESS_IN_DESTINATION` may not use `addresses_place_match`;
  fine at MVP size. If `EXPLAIN` shows seq scans hurting, switch to a per-type predicate built from the resolved
  destination (same keys).
- **Even-split approximation** can hide hotels that could host an uneven split (e.g. 3 adults in a 2-bed + 1-bed).
  Documented in README "Known limitations"; exact packing belongs with C-SRCH-01 pricing.
- **Invalid bearer token on a public endpoint** still returns 401 (resource-server filter runs first), same as
  `/api/auth/login` today. Documented; clients must drop expired tokens.
- **Time zone of "today"**: UTC−12 accepts a check-in that is "yesterday" for travellers far east of it; the hotel's
  own time zone check belongs to booking (C-BOOK-*).
