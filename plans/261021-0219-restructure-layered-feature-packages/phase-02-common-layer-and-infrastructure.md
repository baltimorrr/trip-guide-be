---
phase: 2
title: Common layer and infrastructure
status: completed
priority: P2
dependencies:
  - 1
---

# Phase 2: Common layer and infrastructure

## Overview

Reshape `common/` to the target (`model/ exception/ dto/ config/`), add `BusinessException`, `PageResponse`, and
`JpaAuditingConfig`, add the `local` profile, and rename the compose file and migrations. API responses stay
byte-for-byte compatible.

## Requirements

- Functional: same status codes, problem+json bodies, page JSON shape, audit timestamps.
- Non-functional: `common/` depends on no feature package; no new Maven dependency.

## Architecture

- **Errors**: `BusinessException(HttpStatus status, String message)` + `getStatus()`. `ResourceNotFoundException`
  extends it with `NOT_FOUND`. `GlobalExceptionHandler` replaces `handleBadRequest`/`handleNotFound`/`handleConflict`
  with one `@ExceptionHandler(BusinessException.class)` returning `problem(ex.getStatus(), ex.getMessage())`.
  All other handlers are unchanged.
- **Paging**: the JSON shape must equal today's `PagedModel`:
  ```java
  public record PageResponse<T>(List<T> content, PageMetadata page) {
      public record PageMetadata(long size, long number, long totalElements, long totalPages) {}
      public static <T> PageResponse<T> from(Page<T> page) { /* copy the four numbers */ }
  }
  ```
  Component order = JSON order (`size, number, totalElements, totalPages`, the same as PagedModel).
- **Auditing**: `BaseEntity` gets `@EntityListeners(AuditingEntityListener.class)`, `@CreatedDate` / `@LastModifiedDate`
  in place of Hibernate `@CreationTimestamp` / `@UpdateTimestamp`. `JpaAuditingConfig` = `@Configuration(proxyBeanMethods = false)`
  + `@EnableJpaAuditing`. It is kept off the main class on purpose, so slice tests opt in with `@Import`.
- **Profiles**: `application.yml` sets `spring.profiles.default: local`. `application-local.yml` takes the `.env`
  import and `spring.docker.compose.lifecycle-management`. `prod` (`SPRING_PROFILES_ACTIVE`) and `test`
  (`@ActiveProfiles`) both disable the default, so tests no longer read `.env`.

## Related Code Files

Java paths under `spring-base/src/main/java/com/company/project/`.

| Action | From | To |
|--------|------|----|
| Move + modify | `common/persistence/BaseEntity.java` | `common/model/BaseEntity.java` (auditing annotations, Javadoc) |
| Create | — | `common/config/JpaAuditingConfig.java` |
| Move | `common/error/GlobalExceptionHandler.java` | `common/exception/` (single business handler) |
| Move + modify | `common/error/ResourceNotFoundException.java` | `common/exception/` (extends BusinessException) |
| Create | — | `common/exception/BusinessException.java` |
| Delete | `common/error/BadRequestException.java`, `common/error/ConflictException.java` | — |
| Move | `security/ProblemDetailSecurityHandler.java` | `common/exception/` |
| Move | `security/SecurityConfig.java` | `common/config/` |
| Create | — | `common/dto/PageResponse.java` |
| Modify | `product/ProductController.java` | returns `PageResponse<ProductResponse>` via `PageResponse.from(...)` |
| Modify | `user/UserService.java` | throws `BusinessException(BAD_REQUEST / CONFLICT, …)` |
| Modify | `user/AdminBootstrap.java` | catch only conflicts (see step 4) |
| Modify | `product/Product.java`, `user/User.java`, `auth/AuthService.java`, `product/ProductService.java` | imports |

Resources and root (`spring-base/`):

| Action | From | To |
|--------|------|----|
| Modify | `src/main/resources/application.yml` | drop `.env` import + docker block, add `spring.profiles.default: local` |
| Create | — | `src/main/resources/application-local.yml` |
| Rename | `compose.yaml` | `docker-compose.yml` (Boot auto-detects this name) |
| Rename | `db/migration/V1__create_users.sql` | `V1__create_user_tables.sql` (content unchanged) |
| Rename | `db/migration/V2__create_products.sql` | `V2__create_product_tables.sql` (content unchanged) |
| Modify | `.env.example` | header comment: `.env` is read via `application-local.yml` |

Tests (`spring-base/src/test/java/com/company/project/`):

| Action | From | To |
|--------|------|----|
| Move + rename | `common/error/ErrorResponseTests.java` | `common/exception/ErrorResponseTest.java` |
| Modify | `user/UserRepositoryTests.java` | `@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})` |
| Create | — | `user/AdminBootstrapTest.java` (moves to `user/bootstrap/` in phase 3) |
| Modify | `TestcontainersConfiguration.java` | comment: keep tag in sync with `docker-compose.yml` |

## Implementation Steps

1. `git mv` the common/security files per the table and fix packages/imports. Compile (`./mvnw -q compile`).
2. Add `BusinessException`; make `ResourceNotFoundException` extend it; collapse the three handlers in
   `GlobalExceptionHandler` into one; delete `BadRequestException`/`ConflictException`; update `UserService`.
3. Add `PageResponse`; switch `ProductController.list` to it. Keep the existing explanatory comment, reworded for
   `PageResponse`.
4. `AdminBootstrap`: the old catch of `ConflictException` must not widen into catching every business error.
   An admin password over 72 bytes must still fail startup:
   ```java
   catch (BusinessException | DataIntegrityViolationException ex) {
       if (ex instanceof BusinessException business && business.getStatus() != HttpStatus.CONFLICT) {
           throw business;
       }
       log.info("Admin bootstrap skipped: {} was created concurrently", email);
   }
   ```
5. Add `AdminBootstrapTest` (plain Mockito, no Spring): a `BAD_REQUEST` from `UserService.create` propagates,
   and a `CONFLICT` is swallowed.
6. Switch `BaseEntity` to Spring Data auditing; add `JpaAuditingConfig`; add the `@Import` to `UserRepositoryTests`.
7. Profiles: edit `application.yml`, create `application-local.yml`, update the `.env.example` comment.
8. `git mv compose.yaml docker-compose.yml`; fix both cross-reference comments (compose ↔ TestcontainersConfiguration).
9. `git mv` both migrations. **Local dev DB**: Flyway will reject it ("migration description mismatch") because
   it already applied V1/V2 under the old names. Ask the user which fix to use:
   - keep data: open `docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'` and run
     ```sql
     update flyway_schema_history set description = 'create user tables',    script = 'V1__create_user_tables.sql'    where version = '1';
     update flyway_schema_history set description = 'create product tables', script = 'V2__create_product_tables.sql' where version = '2';
     ```
     Checksums are unaffected because the file content doesn't change.
   - wipe: `docker compose down -v` (destructive; deletes the local database volume).
10. Move/rename `ErrorResponseTests` → `common/exception/ErrorResponseTest`.
11. `./mvnw clean verify` (`clean` again: `target/classes/db/migration` still has the old V1/V2 files, so Flyway would
    see two version-1 migrations).

## Success Criteria

- [ ] `./mvnw clean verify` green; `ProductApiTests` page assertions (`$.page.size`, `$.page.totalElements`)
      pass without edits, which proves the JSON shape is unchanged.
- [ ] `UserRepositoryTests.savesUserWithRolesAndAuditColumns` passes, so auditing fills timestamps.
- [ ] `grep -rnE "import com\.company\.project\.(auth|user|product)" src/main/java/com/company/project/common` is empty.
- [ ] No `common/error`, `common/persistence` dirs remain; `security/` holds only the JWT classes.

## Risk Assessment

- **Slice tests miss auditing**: `@DataJpaTest` doesn't load scanned `@Configuration` classes, so without the
  `@Import`, inserts fail on `created_at not null`. Mitigation: step 6, and the test above catches it.
- **`spring.config.import` in a profile file**: supported by Boot's config-data loader, and verified at runtime in
  phase 4. If `.env` isn't read, the app binds 8080 (taken on this machine) instead of 8081, which fails loudly.
- **Swallowing real errors in AdminBootstrap**: covered by step 4 plus `AdminBootstrapTest`.
- **Local DB**: step 9 needs the user's choice; never run `down -v` without asking.
- Rollback: `git reset --hard HEAD && git clean -fd src` (HEAD = phase 1 commit). If the DB history was edited,
  re-run the two `update`s with the old names (`create users`/`V1__create_users.sql`, `create products`/`V2__create_products.sql`).
