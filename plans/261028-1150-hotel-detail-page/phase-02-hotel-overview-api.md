---
phase: 2
title: Hotel overview API
status: completed
priority: P1
dependencies:
  - 1
---

# Phase 2: Hotel overview API

## Overview

`GET /api/hotels/{slug}` is public and covers C-HOTEL-01 (gallery by category, description, stars, address in one
format, Google and user ratings, map position, distance to centre), C-HOTEL-02 (facilities grouped, free or paid) and
C-HOTEL-03 (check-in/out times, minimum age, policies). The `hotel/` feature gains `controller/`, `services/`,
`dto/` and `mapper/`. All reads are JPQL or derived queries, with no native SQL.

## Context

- `spring-base/CLAUDE.md`:
  - "Architecture": controller → service interface → impl → repository, mappers, entities never leave services
  - "Authentication": a public endpoint needs a matcher in `SecurityConfig` plus an empty `@SecurityRequirements`
  - "Hotel catalog and search": `HotelJpql.HOTEL_LISTED`; suggestions join the COUNTRY row for the country name
- `hotel/repository/HotelRepositoryCustom.java`, `impl/HotelRepositoryImpl.java` (`CARD_SELECT`, the `HOTEL_PHOTOS`
  JPQL style), `HotelJpql.java`
- `destination/repository/impl/DestinationRepositoryImpl.SUGGEST`: the left join on the COUNTRY row, to copy
- `search/controller/SearchController.java`: public endpoint shape. `search/mapper/SearchMapper.label`: joining the
  non-null parts with ", "
- `common/config/SecurityConfig.java` line ~62: the GET `permitAll` matcher for `/api/search/**` and
  `/api/destinations/**`
- Tests: `search/controller/SearchControllerTest`, `wishlist/controller/WishlistControllerTest` (MockMvcTester and
  `CatalogFixtures` usage)

## Requirements

- Functional: the overview, gallery, facilities and policies rules in `plan.md` "Planner Defaults". 404 for an
  unknown, inactive or soft-deleted hotel. No token needed, and an absent token never gives 401.
- Non-functional:
  - At most 4 queries: detail, gallery, facilities, policies.
  - No N+1 over photos or facilities.
  - Search responses don't change.

## Architecture

```text
HotelController GET /api/hotels/{slug}
  └─► HotelService.detail(slug) ──► HotelRepository.findDetail(slug)      JPQL view, listed only, country row left-joined
                                ├─► HotelRepository.findGallery(hotelId)   JPQL, hotel photos then room photos
                                ├─► HotelRepository.findFacilities(id)     JPQL join h.facilities f join f.amenity am
                                └─► HotelPolicyRepository.findByHotelIdOrderBySortOrderAscIdAsc(id)   derived
                                ──► HotelMapper.toDetail(...)              formatted address, groups, policies
```

JPQL to add to `HotelRepositoryImpl`, keeping the comment density of the class:

```sql
-- findDetail: one row per listed hotel; the COUNTRY destination row only names the country
select new com.company.project.hotel.repository.HotelDetailView(
       h.id, h.slug, h.name, t.code, t.name, h.starRating, h.description,
       a.line1, a.ward, a.district, a.city, a.countryCode, c.name, a.latitude, a.longitude,
       h.distanceToCenterM, h.googleRating, h.googleRatingCount, h.userRatingAvg, h.userRatingCount,
       h.checkInFrom, h.checkInUntil, h.checkOutUntil, h.minCheckInAge)
from Hotel h
join h.address a
join h.accommodationType t
left join Destination c on c.countryCode = a.countryCode and c.deletedAt is null
     and c.type = com.company.project.destination.model.DestinationType.COUNTRY
where h.slug = :slug and <HotelJpql.HOTEL_LISTED>

-- findGallery: hotel photos (cover first) before room photos of room types that are sold
select new com.company.project.hotel.repository.GalleryPhotoView(
       p.url, coalesce(p.category, case when p.roomTypeId is not null then 'room' end), p.caption, p.roomTypeId)
from HotelPhoto p
left join RoomType rt on rt.id = p.roomTypeId
where p.hotel.id = :hotelId
  and (p.roomTypeId is null or (rt.status = ACTIVE and rt.deletedAt is null))
order by case when p.roomTypeId is null then 0 else 1 end, p.cover desc, p.sortOrder, p.id

-- findFacilities: the hotel's HOTEL-scope amenities in filter-option order
select new com.company.project.hotel.repository.HotelFacilityView(am.groupName, am.code, am.name, am.icon, f.free)
from Hotel h join h.facilities f join f.amenity am
where h.id = :hotelId and am.scope = com.company.project.hotel.model.AmenityScope.HOTEL
order by am.groupName, am.name
```

Response (`HotelDetail`), with example values:

```json
{
  "id": 3, "slug": "my-khe-beach-resort-demo", "name": "Mỹ Khê Beach Resort",
  "accommodationType": "resort", "accommodationTypeName": "Resort", "starRating": 5,
  "description": "…",
  "address": {"line1": "120 Võ Nguyên Giáp", "ward": "Phước Mỹ", "district": "Sơn Trà", "city": "Đà Nẵng",
              "countryCode": "VN", "countryName": "Vietnam",
              "formatted": "120 Võ Nguyên Giáp, Phước Mỹ, Sơn Trà, Đà Nẵng, Vietnam",
              "latitude": 16.061, "longitude": 108.247},
  "distanceToCenterM": 4200,
  "googleRating": 4.7, "googleRatingCount": null, "userRatingAvg": 9.2, "userRatingCount": 873,
  "photos": [{"url": "…", "category": "exterior", "caption": null, "roomTypeId": null}, …],
  "facilities": [{"name": "Food & drink", "facilities": [{"code": "bar", "name": "Bar", "icon": null, "free": true}]}, …],
  "policies": {"checkInFrom": "14:00", "checkInUntil": null, "checkOutUntil": "12:00", "minCheckInAge": 18,
               "rules": [{"type": "CHILDREN", "description": "…"}, …]}
}
```

`countryName` is null before the sync names the country. `formatted` then ends with the code instead. Times are
wall-clock times in the hotel's zone, serialized as Jackson writes `LocalTime`.

## Related Code Files

Paths under `spring-base/src/main/java/com/company/project/` unless shown in full.

- Create:
  - `hotel/repository/HotelDetailView.java`, `GalleryPhotoView.java`, `HotelFacilityView.java` (records)
  - `hotel/repository/HotelPolicyRepository.java`: `JpaRepository<HotelPolicy, Long>`, one derived query
  - `hotel/dto/HotelDetail.java`, `HotelAddress.java`, `GalleryPhoto.java`, `FacilityGroup.java`, `Facility.java`,
    `HotelPolicies.java`, `HotelPolicyItem.java`
  - `hotel/mapper/HotelMapper.java`
  - `hotel/services/HotelService.java`, `hotel/services/impl/HotelServiceImpl.java`
  - `hotel/controller/HotelController.java`
  - `src/test/java/com/company/project/hotel/controller/HotelControllerTest.java`
  - `src/test/java/com/company/project/hotel/mapper/HotelMapperTest.java`
- Modify: `hotel/repository/HotelRepositoryCustom.java`, `hotel/repository/impl/HotelRepositoryImpl.java`,
  `common/config/SecurityConfig.java` (add `/api/hotels/**` to the GET `permitAll` matcher)
- Delete: none

## Implementation Steps

1. Views and the three JPQL queries in `HotelRepositoryCustom`/`Impl`. Document each method in the interface the
   way the existing ones are.
2. `HotelPolicyRepository` with `List<HotelPolicy> findByHotelIdOrderBySortOrderAscIdAsc(Long hotelId)`.
3. DTO records, documenting the fields that need it with `@param`, as in `HotelSearchResult`/`StayOffer`.
4. `HotelMapper`:
   - `toDetail(view, gallery, facilities, policies)`
   - `formatted` = the non-blank `line1, ward, district, city, countryName ?: countryCode`
   - facility groups are kept in query order (`LinkedHashMap`)
   - policy rules come from the `HotelPolicy` entities
5. `HotelService` and `HotelServiceImpl` (`@Transactional(readOnly = true)`, `ResourceNotFoundException("Hotel",
   slug)`).
6. `HotelController` (`@RequestMapping("/api/hotels")`, `GET /{slug}`, `@SecurityRequirements` with the
   "public: no bearer token in the API docs" comment).
7. `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/search/**", "/api/destinations/**", "/api/hotels/**")`.
8. Tests (below), then `./mvnw test -Dtest='HotelControllerTest,HotelMapperTest'`, then the search test classes to
   confirm they are unchanged.

## Tests

`HotelControllerTest` (`@IntegrationTest`, `CatalogFixtures`, unique suffixes, assert only on own data):

- full overview of a hotel built with description, coordinates, stars, ratings, distance and address parts. Call
  `DestinationSyncService.sync()`, then expect `countryName` and `formatted` to end with the VN country row's current
  name, read via JDBC: other tests may rename countries, so don't hard-code "Vietnam".
- a hotel in a user-assigned country no other test uses (e.g. `QM`): `formatted` ends with `QM`, whether or not some
  sync named the row (the JDK has no name for it).
- the gallery puts the cover first, then hotel photos by `sort_order`, then room photos. A room photo without a
  category reports `room`. A photo of an `INACTIVE` room type is not listed. Categories and captions are passed
  through.
- facilities: grouped by group name and alphabetical, `free` false for a paid one. Only `HOTEL`-scope amenities
  appear, never room features.
- policies: times and minimum age. Rules come in `sort_order`, with their type and description. A hotel without rows
  gets an empty `rules` list.
- 404 for an unknown slug, an `INACTIVE` hotel and a soft-deleted one (`update hotels set deleted_at = now()`).
- no `Authorization` header → 200.

`HotelMapperTest` (unit): blank and null parts are skipped in `formatted`, the code is used when there is no
country name, and facility group order is kept.

## Success Criteria

- [x] The endpoint returns everything in the example, with the rules from `plan.md`.
- [x] 404 cases and public access hold. The API docs show the operation without a bearer requirement.
- [x] The new tests pass. `SearchController*Test`, `WishlistControllerTest` and `DestinationControllerTest` pass
      unchanged.

## Risk Assessment

- **HQL join through an embeddable to an association** (`join h.facilities f join f.amenity am`). Hibernate supports
  it. If it fails, select from `Amenity` with `exists`, or fall back to the link-table entity from phase 1's risk
  note.
- **The JPQL `left join … on` between unrelated entities** (`Destination`, `RoomType` by id). The same pattern is
  already used in `DestinationRepositoryImpl.SUGGEST`.
- **Opening every GET under `/api/hotels/**`** would make a future authenticated GET there public by mistake. Owner
  endpoints should live under their own prefix (e.g. `/api/partner/**`). Note this in CLAUDE.md in phase 5.
- **A bad bearer token on these public GETs still gets 401**, the same known limitation as search. No change.
