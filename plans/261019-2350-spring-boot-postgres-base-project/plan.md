---
title: Spring Boot 4 + PostgreSQL base project with JWT auth
description: >-
  Reusable Maven/Spring Boot 4.1 REST API template in spring-base/ with
  PostgreSQL, Flyway, stateless JWT auth, and a sample CRUD feature.
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - auth
  - infra
blockedBy: []
blocks: []
created: 2026-10-19T00:00:00.000Z
---

# Spring Boot 4 + PostgreSQL base project with JWT auth

## Overview

Generate `/home/jazzy/Personal/java/spring-base/`: a copyable REST API template. Java 21, Spring Boot 4.1.1, Maven wrapper, PostgreSQL 18 via Docker Compose, Flyway, JPA/Hibernate 7, Bean Validation, ProblemDetail errors, Actuator, Swagger UI, Lombok, Spring Security JWT (HS256, built-in Nimbus), role checks, one sample `product` CRUD feature, Testcontainers tests.

Research: [ecosystem versions report](./reports/researcher-261019-2350-spring-boot-4-ecosystem-versions-report.md)

## User Decisions (do not reverse silently)

| Decision | Choice |
|----------|--------|
| Location | New `spring-base/` folder (template); `hotel-booking-system/` untouched |
| Build tool | Maven (`./mvnw`) |
| Scope | Core + JWT security (register/login/me, roles USER/ADMIN) |
| Lombok | Yes, but no `@Data` on entities |

## Planner Defaults (changeable)

- Package `com.example.app`, package-by-feature (`auth/`, `user/`, `product/`, `common/`, `security/`)
- Flyway SQL migrations; `ddl-auto: validate`
- `Long` identity IDs; `@Version` optimistic locking; JPA auditing timestamps
- Secrets via gitignored `.env` imported by `spring.config.import`; no secret defaults in committed config
- Access token only (default 1h TTL); no refresh tokens

## Acceptance Criteria

- [x] `./mvnw spring-boot:run` from `spring-base/` starts Postgres via Compose, applies Flyway, serves `GET /actuator/health` → `UP`
- [x] Register → login → call protected endpoint with bearer token works; bad/expired/tampered token → 401 ProblemDetail JSON
- [x] USER can read products; only ADMIN can create/update/delete (403 ProblemDetail otherwise)
- [ ] Swagger UI at `/swagger-ui.html` with working "Authorize" bearer button (non-prod) — *spec verified (bearerAuth scheme, public login/register); manual UI click-through not done*
- [x] `./mvnw verify` green with Testcontainers (Docker running); no secrets committed
- [x] README explains setup, env vars, run, test, and how to copy the template

## Implementation Notes (2026-10-20)

All phases built and verified: `./mvnw verify` 32/32 green; manual curl flow, prod profile, and fresh-copy README run checked. Deviations from the phase files:

- Added `user/UserService`: one account-creation path (normalize, 72-byte check, duplicate check, hash) shared by register and admin bootstrap
- `AdminBootstrapProperties` is a top-level record; bootstrap admin password must be ≥ 12 chars
- `TestAuth` helper created in phase 3 (not 4); `Product` uses an `update()` method instead of setters
- Added `server.port: ${SERVER_PORT:8080}`: 8080/5432 were already taken on the dev machine
- `app.jwt.secret: ${APP_JWT_SECRET:}` (empty default): unresolved placeholders bind as literal text, giving a misleading error; empty now fails `@NotBlank` with Boot's report naming the property
- Spring Data 4 moved `PropertyReferenceException` to `org.springframework.data.core`
- README warns that Compose names the DB after the folder: a copy still named `spring-base` shares the original's database (found by the fresh-copy test)
- Red-team review and post-implementation tester/code-reviewer agents were skipped at the user's request

## Not In Scope (deferred)

Refresh tokens/revocation, login rate limiting, email verification/password reset, CI pipeline, Dockerfile (use `spring-boot:build-image`), MapStruct, metrics/tracing, API versioning, frontend.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Scaffold project and infrastructure](./phase-01-scaffold-project-and-infrastructure.md) | Completed |
| 2 | [Web and persistence foundations](./phase-02-web-and-persistence-foundations.md) | Completed |
| 3 | [JWT authentication and authorization](./phase-03-jwt-authentication-and-authorization.md) | Completed |
| 4 | [Sample product feature and README](./phase-04-sample-product-feature-and-readme.md) | Completed |

## Dependencies

- Phases strictly sequential: 1 → 2 → 3 → 4 (each builds on prior code)
- Cross-plan: none (no other plans exist)
- Runtime: JDK 21 (installed), Docker daemon running (installed), internet for Maven Central first build
