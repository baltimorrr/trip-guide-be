---
phase: 4
title: Docs and final verification
status: completed
priority: P2
dependencies:
  - 3
---

# Phase 4: Docs and final verification

## Overview

Rewrite the README sections that describe structure and conventions, then check the app end to end against a
real database.

## Requirements

- Functional: README matches the code; the app runs locally with `.env` and serves the same API.
- Non-functional: README sections not about structure (endpoints, limitations, production notes) stay as they are,
  apart from names.

## Related Code Files

- Modify: `spring-base/README.md`
- No code changes expected. Any fix found here goes back to the phase that owns the file.

## Implementation Steps

1. **README** (read it fully first):
   - *Project structure*: replace the tree with the final layout from [plan.md](./plan.md), with a one-line role per folder.
     Replace "Code is grouped by feature..." with: feature first, then layer (`controller/ model/ repository/
     services/` interface + `services/impl/`, `dto/ mapper/`); `auth/security/` holds JWT/login infrastructure.
   - *Adding a feature*: update the checklist paths: `V{next}__create_thing_tables.sql`, `thing/model/Thing`,
     `thing/repository/ThingRepository`, `thing/dto/*`, `thing/mapper/ThingMapper`, `thing/services/ThingService` +
     `impl/ThingServiceImpl`, `thing/controller/ThingController`; tests `controller/ThingControllerTest`
     (`@IntegrationTest`) + `services/impl/ThingServiceImplTest` (Mockito).
   - *Conventions*: errors are `BusinessException(HttpStatus, message)` / `ResourceNotFoundException`; mappers own
     DTO↔entity conversion; timestamps come from Spring Data auditing, so `@DataJpaTest` classes must
     `@Import(JpaAuditingConfig.class)`.
   - *Tests* table: new class names + `UserServiceImplTest`, `ProductServiceImplTest`, `AdminBootstrapTest` (unit).
   - *Configuration*: profiles: `local` (default; reads `.env`, starts Compose), `prod`, `test`.
   - *Using this as a template*: package `com.company.project`, `ProjectApplication`, groupId `com.company`,
     `V2__create_product_tables.sql`.
   - Endpoints table and JSON examples: no change (the contract is preserved). Re-read them to confirm.
2. **Static checks** (from `spring-base/`):
   ```bash
   grep -rn "com\.example\|compose\.yaml\|common/error\|common/persistence\|BadRequestException\|ConflictException\|import .*PagedModel" \
     --exclude-dir=target --exclude-dir=.git --exclude-dir=.idea .          # expect no hits
   grep -rnE "import com\.company\.project\.(auth|user|product)" src/main/java/com/company/project/common   # expect none
   find src -type d -empty                                                  # expect none
   ```
3. **Full build**: `./mvnw clean verify`. Expected test total = phase 1 baseline N + 9 new
   (UserServiceImpl 3, ProductServiceImpl 4, AdminBootstrap 2). A lower number means a test was lost in a move.
4. **Smoke run** (ports from `.env`: app 8081, Postgres 5434; never touch the user's other containers):
   - `./mvnw spring-boot:run` in the background; poll `http://localhost:8081/actuator/health` until `UP`.
     If it binds 8080 instead, the `local` profile's `.env` import is broken (phase 2).
   - curl register → login → `/api/auth/me` → `GET /api/products?page=0&size=5`, and check the body has
     `content` and `page.{size,number,totalElements,totalPages}`.
   - If `.env` sets `APP_BOOTSTRAP_ADMIN_PASSWORD`: admin login, then create/update/delete a product (201 + `Location`, 200, 204).
   - USER token on `POST /api/products` → 403 problem+json.
   - Stop the app: `pkill -f "[c]om.company.project.ProjectApplication"` (bracket so it doesn't match itself).
5. Commit (with the user's go-ahead): `docs: describe layered feature package structure`, after the
   `refactor:` commits from phases 2–3.

## Success Criteria

- [ ] All static checks clean.
- [ ] `./mvnw clean verify` green with N + 9 tests.
- [ ] Smoke calls return the documented status codes and JSON shapes.
- [ ] README claims (paths, class names, profile names, commands) match the code. Spot-check every path it lists.

## Risk Assessment

- **README drift**: the doc lists many paths. Mitigation: check every path in the structure section with `ls`.
- **Port clash**: if 8081/5434 are busy, override via env for the run only; don't edit the user's `.env` without asking.
