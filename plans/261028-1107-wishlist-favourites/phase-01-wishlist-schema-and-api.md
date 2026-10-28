---
phase: 1
title: Wishlist schema and API
status: completed
priority: P1
dependencies: []
---

# Phase 1: Wishlist schema and API

## Overview

A new `wishlist/` feature in the standard shape: a V7 table, a composite-key entity, a repository, a service, a
mapper and a controller. It adds idempotent save, idempotent remove and a paged list of hotel cards with
`available` and `savedAt`. Hotel code gains one derived query, and search is not touched.

## Context

- `spring-base/CLAUDE.md`:
  - composite natural keys stay without id/version, like `user_roles`
  - JPQL first, native SQL only where JPQL can't, with a comment saying why
  - a repository with nothing for Spring Data to generate is a plain interface + `@Repository` impl
    (`DestinationSyncRepository`)
  - JPQL bulk statements bypass auditing
  - `PageResponse.from(page)`
- `auth/controller/ProfileController.java`: `@AuthenticationPrincipal Jwt` and `userId(jwt)`
- `hotel/repository/HotelRepository.java`, `HotelRepositoryCustom.findCardsByIdIn` (the card select, no listing
  filter), `HotelJpql.HOTEL_LISTED`, `HotelCardView`
- `search/services/impl/SearchServiceImpl.results`: the "ids page, then cards by id" pattern
- `db/migration/V1__create_user_tables.sql` (`user_roles`), `V6__...sql` header comment style
- Tests: `support/IntegrationTest`, `TestAuth.userToken`/`bearer`, `CatalogFixtures.hotel(...)`/`hotelStatus`,
  `SearchControllerSortTest` (shape of a catalog-backed MockMvcTester test)

## Requirements

- Functional: the endpoints and rules in `plan.md` "Planner Defaults" and "User Decisions".
- Non-functional:
  - Saving is a single statement, so a double tap or a concurrent save never gives 409.
  - The list costs at most 3 queries (entries page, count, cards).
  - The user id never comes from the path or body.

## Architecture

```text
WishlistController ──► WishlistService ──► WishlistRepository (plain interface, @Repository impl, EntityManager)
  /api/wishlist          │                    addIfAbsent: HQL insert … on conflict do nothing
                         │                    remove:      JPQL bulk delete
                         │                    findPage:    JPQL entries ⨝ Hotel → (hotelId, savedAt, available)
                         └──► HotelRepository existsByIdAndStatusAndDeletedAtIsNull (PUT 404 check)
                                              findCardsByIdIn (cards for the page's ids)
```

List flow: `findPage(userId, pageable)` → page of `WishlistEntryView`. If the page is empty, return it as is.
Otherwise `hotels.findCardsByIdIn(ids)` → map by id → `entries.map(e -> mapper.toResponse(cards.get(e.hotelId()), e))`,
which keeps the entry order and the page totals. The FK guarantees every hotel row exists, soft-deleted ones
included, and `findCardsByIdIn` doesn't filter by listing, so every entry gets its card.

## Related Code Files

Paths under `spring-base/src/main/java/com/company/project/` unless shown in full.

- Create: `spring-base/src/main/resources/db/migration/V7__create_wishlist_table.sql`
- Create: `wishlist/model/WishlistEntry.java`
- Create: `wishlist/repository/WishlistRepository.java`, `wishlist/repository/WishlistEntryView.java`,
  `wishlist/repository/impl/WishlistRepositoryImpl.java`
- Create: `wishlist/dto/WishlistHotel.java`, `wishlist/mapper/WishlistMapper.java`
- Create: `wishlist/services/WishlistService.java`, `wishlist/services/impl/WishlistServiceImpl.java`
- Create: `wishlist/controller/WishlistController.java`
- Modify: `hotel/repository/HotelRepository.java` (one derived query)
- Create tests: `spring-base/src/test/java/com/company/project/wishlist/controller/WishlistControllerTest.java`,
  `.../wishlist/services/impl/WishlistServiceImplTest.java`

That is more than 8 files, but each one is the README checklist's standard feature shape. The mapper and view follow
feature conventions, and nothing else is new.

## Implementation Steps

0. Back up `spring-base/` (excluding `target/`) to the scratchpad tgz from `plan.md` "Rollback". Optionally run
   `./mvnw verify` once and record the test count as the baseline.

1. **Migration `V7__create_wishlist_table.sql`.** Write a header comment in the style of V6: names follow the doc,
   the composite key replaces id/version like `user_roles`, and `created_at` is set by the application because rows
   are written by one idempotent statement with no auditing.
   ```sql
   create table wishlists (
       user_id    bigint      not null references users (id) on delete cascade,
       hotel_id   bigint      not null references hotels (id) on delete cascade,
       created_at timestamptz not null,
       primary key (user_id, hotel_id)
   );
   ```
   No other index, as in the doc: the PK serves "this user's rows", and one user's list is small to sort.

2. **Entity `WishlistEntry`.** It is the only entity without `BaseEntity`, because the table has no id, version or
   `updated_at`. Javadoc says so, and says the entity is written only by the repository's statements (no `create`
   factory: it is never built in Java).
   ```java
   @Entity
   @Table(name = "wishlists")
   @IdClass(WishlistEntry.Key.class)
   @Getter
   @NoArgsConstructor(access = AccessLevel.PROTECTED)
   public class WishlistEntry {
   	@Id @Column(name = "user_id") private Long userId;
   	@Id @Column(name = "hotel_id") private Long hotelId;
   	@Column(name = "created_at", nullable = false) private Instant createdAt;

   	/** Primary key: one entry per user and hotel. */
   	public record Key(Long userId, Long hotelId) implements Serializable { }
   }
   ```
   If Hibernate rejects a record `@IdClass`, use a static nested class with a no-arg constructor and
   `equals`/`hashCode`.

3. **Repository.** This is a plain interface plus a `@Repository` impl, because Spring Data has nothing to generate
   here. Add it to the CLAUDE.md line about `DestinationSyncRepository` in phase 2.
   ```java
   public interface WishlistRepository {
   	/** Saves the hotel for the user unless already saved (keeps the first savedAt). @return rows added, 0 or 1 */
   	int addIfAbsent(long userId, long hotelId, Instant savedAt);
   	/** @return rows removed, 0 or 1 */
   	int remove(long userId, long hotelId);
   	/** The user's entries, newest first (ties: higher hotel id first), each with whether its hotel is listed. */
   	Page<WishlistEntryView> findPage(long userId, Pageable pageable);
   }
   public record WishlistEntryView(Long hotelId, Instant savedAt, boolean available) { }
   ```
   Impl (`EntityManager`). The JPQL:
   ```java
   // One statement, so saving twice (or two taps at once) never hits the primary key: Hibernate 7 HQL insert.
   private static final String ADD = """
   		insert into WishlistEntry (userId, hotelId, createdAt) values (:userId, :hotelId, :savedAt)
   		on conflict do nothing""";
   private static final String REMOVE =
   		"delete from WishlistEntry w where w.userId = :userId and w.hotelId = :hotelId";
   private static final String PAGE = """
   		select new com.company.project.wishlist.repository.WishlistEntryView(w.hotelId, w.createdAt,
   		       case when %s then true else false end)
   		from WishlistEntry w
   		join Hotel h on h.id = w.hotelId
   		where w.userId = :userId
   		order by w.createdAt desc, w.hotelId desc""".formatted(HotelJpql.HOTEL_LISTED);
   private static final String COUNT = "select count(w) from WishlistEntry w where w.userId = :userId";
   ```
   For paging, use `setFirstResult(offset)` and `setMaxResults(size)`, plus the count, the way `HotelRepositoryImpl`
   pages (or `PageableExecutionUtils.getPage` to skip the count on a short first page). The bulk delete runs no row
   count check, so two concurrent DELETEs both succeed. Loading the entity and calling `remove` would throw on the
   second one.

   **Do this first:** a quick integration check (the first controller test below) that the HQL `on conflict` insert
   works with the `@IdClass` entity. Fallback: a native `insert into wishlists ... on conflict do nothing`, with a
   comment saying why JPQL wasn't used, as CLAUDE.md requires. If the constructor expression rejects the `case`
   boolean for a primitive `boolean`, make the view field `Boolean`.

4. **`HotelRepository`.** Add `boolean existsByIdAndStatusAndDeletedAtIsNull(Long id, ListingStatus status);`, a
   derived query that matches the existing `findBySlugAndStatusAndDeletedAtIsNull`.

5. **DTO and mapper.**
   ```java
   /** A saved hotel. {@code available} is false once the hotel is no longer listed (it can't be opened or booked). */
   public record WishlistHotel(Long id, String slug, String name, String accommodationType, Short starRating,
   		String coverPhotoUrl, String district, String city, String countryCode, BigDecimal googleRating,
   		Integer googleRatingCount, BigDecimal userRatingAvg, int userRatingCount, Integer distanceToCenterM,
   		boolean available, Instant savedAt) { }
   ```
   `WishlistMapper` (`@Component`) has `toResponse(HotelCardView card, WishlistEntryView entry)`.

6. **Service.** `WishlistService` has `list(long userId, Pageable)`, `save(long userId, long hotelId)` and
   `remove(long userId, long hotelId)`, with Javadoc stating the 404 and the idempotency. The impl is
   `@Transactional(readOnly = true)` on the class and `@Transactional` on `save`/`remove`:
   - `save`: if the hotel isn't listed, `throw new ResourceNotFoundException("Hotel", hotelId)`. Otherwise call
     `wishlists.addIfAbsent(userId, hotelId, Instant.now())`.
   - `remove`: `wishlists.remove(userId, hotelId)`. There is no listing check, so unavailable hotels can still be
     removed.
   - `list`: as in Architecture. Return early on an empty page without the card query, because an empty `in :ids` is
     avoided elsewhere too.

7. **Controller `WishlistController`.** Mapped at `/api/wishlist`, it injects `WishlistService` and needs no
   `SecurityConfig` change (`anyRequest().authenticated()`). The OpenAPI default already requires bearer auth.
   - `GET` → `PageResponse.from(service.list(userId(jwt), pageable))`, with `@ParameterObject
     @PageableDefault(size = 20)`. Javadoc: newest first, `sort` ignored, and clients draw result-card hearts from
     these ids.
   - `PUT /{hotelId}` and `DELETE /{hotelId}`: `@ResponseStatus(HttpStatus.NO_CONTENT)`, with `@PathVariable long
     hotelId`. A non-numeric id becomes a 400 through the existing `ResponseEntityExceptionHandler` base.
   - `private static long userId(Jwt jwt)`: the same one-liner as `ProfileController`. `common/` can't own it, and a
     shared helper isn't worth depending on `auth/`.

8. **Tests.** Write the service unit test (Mockito), then the controller test (`@IntegrationTest`). A fresh
   `TestAuth.userToken` user owns a wishlist that no other test writes, so asserting its `totalElements` or emptiness
   is safe. Each hotel uses `uniqueSuffix()`.

## Tests

`WishlistControllerTest` (`@IntegrationTest`, `MockMvcTester`, `CatalogFixtures`):

- `savingTwiceKeepsOneEntry`: PUT, PUT → 204, 204. GET → `totalElements` 1, `id`/`slug`/`name` of the hotel,
  `available` true, `savedAt` present, and `savedAt` unchanged after the second PUT.
- `listsNewestFirstInPages`: save A, B, C. `size=2` → C, B. `page=1` → A. `totalElements` 3. `sort=name,asc` changes
  nothing.
- `removingIsIdempotent`: save, then DELETE → 204, and it's gone from GET. DELETE again → 204. DELETE of a hotel
  never saved → 204.
- `onlyListedHotelsCanBeSaved`: an unknown id → 404 problem+json. An `INACTIVE` hotel → 404. A soft-deleted hotel
  (inline `jdbc` update of `deleted_at`) → 404. `/api/wishlist/abc` → 400.
- `delistedHotelsStayAsUnavailable`: save, then `catalog.hotelStatus(id, "INACTIVE")` → `available` false with the
  card data still there. Back to `ACTIVE` → true. Soft-deleted → false. The unavailable entry can still be
  DELETEd.
- `eachUserHasTheirOwnWishlist`: A saves. B's GET doesn't contain it. B's DELETE of that hotel id leaves A's entry.
- `requiresSignIn`: GET, PUT and DELETE without a token → 401.

`WishlistServiceImplTest` (Mockito, real `WishlistMapper`):

- `saveRejectsAHotelThatIsNotListed`: 404, and `addIfAbsent` is never called.
- `saveAddsAListedHotel`: `addIfAbsent(userId, hotelId, any())`.
- `listKeepsTheWishlistOrderAndAvailability`: the cards come back in a different order, and the result follows the
  entries and keeps the totals and `available` flags.
- `emptyPageSkipsTheCardQuery`: `findCardsByIdIn` is never called.

Run the narrow tests first (`./mvnw test -Dtest='Wishlist*'`), then the existing search tests, to show nothing
moved.

## Success Criteria

- [x] App starts: Flyway applies V7, and `ddl-auto: validate` accepts `WishlistEntry`.
- [x] Every test above passes, and HQL `on conflict do nothing` is proven or replaced by the documented native
      fallback.
- [x] No change to `search/`, `SecurityConfig` or existing tests.

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| HQL `insert … on conflict` with an `@IdClass` entity is new to this codebase | First test proves it. Fallback: native statement with a "why" comment |
| Record as `@IdClass` not accepted | Static nested key class with `equals`/`hashCode` |
| `case` boolean vs primitive in `select new` | `Boolean` view field |
| Hotel hard-deleted between the 404 check and the insert | FK violation → 409 from the existing handler. Rare, because hotels are soft-deleted |
| Large wishlists and hearts | Client pages with `size=100` (user decision: no ids endpoint). Documented in phase 2 |

**Threat model.** The table stores which listed hotels a user likes, which is personal data but low sensitivity.
- **IDOR:** the user id comes only from the validated token (`sub`; the session is checked on every request).
  `eachUserHasTheirOwnWishlist` covers this.
- **Hotel probing:** a PUT 404 only reveals whether a hotel id is listed, which search already shows publicly.
- **Row flooding:** rows per user are bounded by the number of listed hotels, so no cap is needed.
- **Account deletion:** `on delete cascade` from `users` removes the wishlist, as the privacy story asks.
