---
phase: 4
title: Reviews API
status: completed
priority: P2
dependencies:
  - 1
  - 2
---

# Phase 4: Reviews API

## Overview

`GET /api/hotels/{slug}/reviews?page&size&sort` is public and covers C-HOTEL-07: a hotel's published reviews,
paged, newest first or by score. It adds a new `review/` feature in the standard shape. Reviews are read-only:
nothing writes them until C-MYBK-05, and the demo seed has none (user decision), so locally the list is empty. Tests
insert their own.

## Context

- `spring-base/CLAUDE.md`:
  - "Architecture"
  - "Conventions": `PageResponse.from(page)`, the global page size cap of 100, and unknown sort properties → 400 in
    `GlobalExceptionHandler`
  - Public endpoint rules
- `product/` (paged list with `Pageable` sorting end to end), `wishlist/` (a feature over hotels, its
  `PageResponse` use and its test shape)
- Phase 1: `review/model/Review`, `ReviewStatus`, `TravellerType`, V8 `reviews`. Phase 2: the `/api/hotels/**` GET
  matcher.
- `hotel/repository/HotelRepository.findBySlugAndStatusAndDeletedAtIsNull`

## Requirements

- Functional:
  - Only `PUBLISHED` reviews of the given listed hotel. An unknown or unlisted hotel gives 404.
  - Order: newest first by default. `sort=createdAt` or `sort=score`, either direction. Ties go to the newest, then
    to the higher id. Any other sort property gives 400 "Reviews can be sorted by createdAt or score".
  - Items: `id, score, title, body, travellerType, createdAt, ownerReply, ownerRepliedAt`.
  - No reviewer identity, and no rating summary (the overview has `userRatingAvg`/`userRatingCount`).
- Non-functional: one page query plus one count query (Spring Data). The sort is checked before any query runs.

## Architecture

```text
ReviewController GET /api/hotels/{slug}/reviews   (@SecurityRequirements, @PageableDefault(size = 10, sort = createdAt desc))
  └─► ReviewService.list(slug, pageable)
        ├─ check the sort properties ⊆ {createdAt, score}; append createdAt desc (unless already sorted by it), then id desc
        ├─► HotelRepository.findBySlugAndStatusAndDeletedAtIsNull(slug, ACTIVE)            404
        └─► ReviewRepository.findByHotelIdAndStatus(hotelId, PUBLISHED, pageRequest)       derived, Page<Review>
              └─► ReviewMapper.toResponse  →  PageResponse.from(page)
```

- `ReviewRepository extends JpaRepository<Review, Long>` declares only the derived query. There's no
  `ReviewRepositoryCustom` because nothing is hand-written. It is served by the V8 partial index
  `(hotel_id, created_at desc) where status = 'PUBLISHED'`.
- The sort check lives in the service, not in the controller.

## Related Code Files

Paths under `spring-base/src/main/java/com/company/project/` unless shown in full.

- Create: `review/repository/ReviewRepository.java`, `review/dto/HotelReview.java`, `review/mapper/ReviewMapper.java`,
  `review/services/ReviewService.java`, `review/services/impl/ReviewServiceImpl.java`,
  `review/controller/ReviewController.java`; tests
  `src/test/java/com/company/project/review/controller/ReviewControllerTest.java`,
  `src/test/java/com/company/project/review/repository/ReviewRepositoryTest.java`,
  `src/test/java/com/company/project/review/services/impl/ReviewServiceImplTest.java`
- Modify: `src/test/java/com/company/project/support/CatalogFixtures.java`
  - `long user()`: a minimal `users` row with a unique `reviewer-<suffix>@test.local` email, an unused password hash
    and a full name
  - `ReviewBuilder review(long hotelId, long userId, int score)` with `title`, `body`, `travellerType`, `hidden()`,
    `createdAt(Instant)`, `ownerReply(String, Instant)` and `long create()`. It inserts a unique fake `booking_id`
    (there's no bookings table yet, so no FK) and sets `created_at`/`updated_at` from `createdAt` or now.
- Delete: none

## Implementation Steps

1. `ReviewRepository` with `Page<Review> findByHotelIdAndStatus(Long hotelId, ReviewStatus status, Pageable pageable)`.
2. `HotelReview` record and `ReviewMapper.toResponse(Review)`.
3. `ReviewService`/`ReviewServiceImpl` (`@Transactional(readOnly = true)`). Validate the sort, build the
   `PageRequest` with the tie-breakers, then resolve the hotel (404) and query.
4. `ReviewController` at `/api/hotels/{slug}/reviews`, returning `PageResponse.from(...)`, with the public
   `@SecurityRequirements` comment.
5. Fixtures, then the tests below. Run `./mvnw test -Dtest='Review*Test'`.

## Tests

`ReviewControllerTest` (`@IntegrationTest`, a hotel and users of its own):
- default order is newest first, with every field (including an owner reply). Paging with `size=2` reports
  `totalElements`/`totalPages` for the published reviews only.
- `sort=score,desc` gives the highest first, and equal scores newest first. `sort=score,asc` gives the lowest first.
  `sort=createdAt,asc` gives the oldest first.
- hidden reviews and other hotels' reviews are never listed or counted.
- `sort=body` and `sort=id` → 400 problem+json.
- unknown slug or `INACTIVE` hotel → 404. A listed hotel without reviews → an empty page. No token → 200.

`ReviewRepositoryTest` (`@IntegrationTest`, like `DestinationRepositoryTest`; the V8 guards):
- a second review with the same `booking_id` is rejected (unique)
- a score of 0 or 11 is rejected
- an unknown `traveller_type` or `status` is rejected
- the derived query returns only `PUBLISHED` rows of that hotel

`ReviewServiceImplTest` (Mockito):
- unsorted becomes `createdAt desc, id desc`
- `score asc` becomes `score asc, createdAt desc, id desc`
- an unsupported property gives 400 before any repository call
- an unknown hotel gives 404 without a review query

## Success Criteria

- [x] The endpoint pages published reviews with the documented order, fields and errors.
- [x] V8's review constraints are covered by tests.
- [x] The demo seed still has no reviews. The README in phase 5 says why the local list is empty.

## Risk Assessment

- **Fake booking ids in tests.** These rows exist only in the throwaway Testcontainers database. The future bookings
  migration adds the FK on a fresh test database before any test inserts. Locally there are no reviews, so the FK
  can be added there too.
- **A stored rating summary vs the review rows.** `hotels.user_rating_avg/count` stay as stored (seeded, and read by
  search). They are not recomputed from `reviews` until reviews can be written. Document this as a known limitation
  in phase 5.
- **No cascade from hotels or users to reviews** (it follows the schema doc). Deleting a user with reviews fails on
  the FK. There's no account-deletion endpoint yet, so the data-privacy story must decide later (anonymize or
  delete).
