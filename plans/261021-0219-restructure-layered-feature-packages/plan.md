---
title: Restructure spring-base into layered feature packages
description: >-
  Move spring-base from flat feature packages to feature-then-layer packages
  (controller/model/repository/services+impl/dto/mapper), rename the base
  package to com.company.project, and align common/, config, compose and
  migration names with the user's target tree. No new business features.
status: completed
priority: P2
tags:
  - backend
  - refactor
blockedBy: []
blocks: []
created: 2026-10-21T00:00:00.000Z
---

# Restructure spring-base into layered feature packages

## Overview

Target: the user's tree (feature first, layer second). Scope is **restructure only**: every behavior and API
response stays the same, and the existing integration tests prove it. Builds on the completed plan
[261019-2350-spring-boot-postgres-base-project](../261019-2350-spring-boot-postgres-base-project/plan.md).

Final layout (`spring-base/src/main/java/com/company/project/`):

```text
ProjectApplication.java
common/
  model/BaseEntity.java                 Spring Data auditing (was Hibernate timestamps)
  exception/GlobalExceptionHandler, ResourceNotFoundException, BusinessException, ProblemDetailSecurityHandler
  dto/PageResponse.java                 same JSON shape as today's PagedModel
  config/JpaAuditingConfig, SecurityConfig, OpenApiConfig
auth/      controller/ services/(+impl/) dto/ security/(JwtConfig, JwtProperties, TokenService, AppUserDetailsService)
user/      model/ repository/ services/(+impl/) dto/ mapper/ bootstrap/(AdminBootstrap + properties)
product/   controller/ model/ repository/ services/(+impl/) dto/ mapper/
```

## User Decisions (do not reverse silently)

| Decision | Choice |
|----------|--------|
| Scope | Restructure only. No order module, Category, ProductStatus, UserStatus, UserController (follow-up plan) |
| Naming | Package `com.company.project`, main class `ProjectApplication`, Maven groupId `com.company`; folder/artifactId stay `spring-base` |
| Mappers | Hand-written `@Component` classes, no MapStruct |
| Services | Interface in `services/`, implementation in `services/impl/` (from target tree) |

## Planner Defaults (changeable)

- `auth/` kept as its own module; JWT + login infrastructure lives in `auth/security/`.
- `BusinessException(HttpStatus, message)` replaces `BadRequestException`/`ConflictException`; `ResourceNotFoundException` extends it.
- `PageResponse` reproduces `{content, page:{size, number, totalElements, totalPages}}`, so there is no client-visible change.
- Existing tests kept, moved to mirror packages, suffix `Tests` → `Test`.
- No empty placeholder folders (no `user/controller/` until there is a user API).
- Default Spring profile `local` holds `.env` import + Docker Compose settings.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Baseline and package rename](./phase-01-baseline-and-package-rename.md) | Completed |
| 2 | [Common layer and infrastructure](./phase-02-common-layer-and-infrastructure.md) | Completed |
| 3 | [Feature layering](./phase-03-feature-layering.md) | Completed |
| 4 | [Docs and final verification](./phase-04-docs-and-final-verification.md) | Completed |

Strictly sequential. Every phase ends with `./mvnw clean verify` green and can be committed on its own.

## Dependencies

- Completed plan `261019-2350-spring-boot-postgres-base-project`, no blocking relation.
- Docker running (Testcontainers + Compose). Local ports: app 8081, Postgres 5434 (from `spring-base/.env`).

## Acceptance Criteria

- [ ] Source tree matches the layout above; `grep -r "com.example" spring-base --exclude-dir=target --exclude-dir=.git` is empty.
- [ ] `common/` imports nothing from `auth`, `user`, or `product`.
- [ ] All existing tests pass unchanged in assertions (only package/name/imports change), plus new `UserServiceImplTest`, `ProductServiceImplTest`, `AdminBootstrapTest`.
- [ ] Every endpoint returns the same status codes and JSON shapes as before (README endpoint table unchanged).
- [ ] App starts with `./mvnw spring-boot:run` on the `.env` port; register/login/me/products smoke calls succeed.
- [ ] README structure, feature checklist, tests table, and template notes match the new layout.

## Out of Scope

Order module, `Category`, `ProductStatus`, `UserStatus`, `UserController`/`UserRequest`, MapStruct, renaming the `spring-base` folder, any API contract change.

## Outcome (2026-10-21)

All 4 phases done. `./mvnw clean verify`: 42 tests green (baseline 32 + 9 planned unit tests + 1 timestamp guard).
Smoke run on the `local` profile: register/login/me, product CRUD, 401/403/404/409/400 problem+json all as documented.

Deviations from the plan:
- **No git commits** (user's choice). Pre-refactor copy kept in the session scratchpad instead.
- **Timestamp precision**: Spring Data auditing stamped nanoseconds, so write responses differed from the stored
  microsecond value (Hibernate used to truncate). Fixed with a microsecond-truncating `DateTimeProvider` in
  `JpaAuditingConfig`, guarded by `ProductControllerTest.writeResponseTimestampsMatchWhatIsStored` (verified it fails without the fix).
- Local dev DB: Flyway history rows renamed in place (data kept), not wiped.
- `src/main/resources/{static,templates}` (pre-existing empty dirs) kept.
