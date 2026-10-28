---
phase: 1
title: Scaffold project and infrastructure
status: completed
priority: P1
dependencies: []
effort: 1h
---

# Phase 1: Scaffold project and infrastructure

## Overview

Generate `spring-base/` from Spring Initializr (Boot 4.1.1, Java 21, Maven), pin infra (Postgres 18 via Compose), set base config + secret handling, and prove the app boots against a real DB in dev and in tests.

Context: [research report](./reports/researcher-261019-2350-spring-boot-4-ecosystem-versions-report.md), [plan](./plan.md)

## Key Insights

- Boot 4 starter names differ from Boot 3 tutorials (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, per-module `*-test` starters). Generating from Initializr avoids hand-typed mistakes.
- **Security deps NOT added here.** With Spring Security on the classpath, Boot locks every endpoint behind a generated password, which would break this phase's health checks. Phase 3 adds security.
- `spring-boot-docker-compose` auto-runs `compose.yaml` on `spring-boot:run` and wires the datasource. It is skipped in tests by default, and the `spring-boot-maven-plugin` leaves it out of the packaged jar.
- Tests use Testcontainers `@ServiceConnection`, so they need no datasource properties.

## Requirements

- Functional: `./mvnw spring-boot:run` starts Postgres + app; `GET /actuator/health` → `{"status":"UP"}`
- Functional: `./mvnw verify` boots the full context against a Testcontainers Postgres
- Non-functional: no secrets in committed files; image tags pinned (no `latest`); `.env` gitignored

## Architecture

Target tree after all phases (this phase creates the items marked `*`):

```text
spring-base/
├── pom.xml *  mvnw * mvnw.cmd * .mvn/wrapper/ *
├── compose.yaml *  .env.example *  .gitignore *  .gitattributes *  README.md (phase 4)
└── src/
    ├── main/java/com/example/app/
    │   ├── Application.java *
    │   ├── common/{error,persistence,config}/   (phase 2)
    │   ├── security/                             (phase 3)
    │   ├── user/  auth/                          (phase 3)
    │   └── product/                              (phase 4)
    ├── main/resources/
    │   ├── application.yml *  application-prod.yml (phase 2)
    │   └── db/migration/V1__…sql (phase 3), V2__…sql (phase 4)
    └── test/java/com/example/app/
        ├── ApplicationTests.java *  TestApplication.java *  TestcontainersConfiguration.java *
        └── support/IntegrationTest.java *
        test/resources/application-test.yml *
```

Config and secret flow:

```text
.env (gitignored) ──► docker compose (variable interpolation: POSTGRES_*)
      └────────────► Spring (spring.config.import: optional:file:.env[.properties]) ──► ${APP_*} placeholders
```

## Related Code Files

- Create: everything under `/home/jazzy/Personal/java/spring-base/` listed with `*` above
- Delete: `spring-base/HELP.md` (generated noise)
- Do not touch: `/home/jazzy/Personal/java/exercise/`, `/home/jazzy/Personal/java/hotel-booking-system/`

## Implementation Steps

1. Generate the project (run from `/home/jazzy/Personal/java`; the zip goes in a scratch dir):
   ```bash
   curl -fsS -o "$SCRATCH/spring-base.zip" "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.1&javaVersion=21&groupId=com.example&artifactId=spring-base&name=spring-base&packageName=com.example.app&applicationName=Application&dependencies=web,data-jpa,postgresql,flyway,validation,actuator,docker-compose,testcontainers,lombok,configuration-processor"
   unzip -q "$SCRATCH/spring-base.zip" -d spring-base && rm spring-base/HELP.md
   ```
   Then check that `pom.xml` contains `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `testcontainers-postgresql`, and that Lombok is in `annotationProcessorPaths`.
2. Replace `compose.yaml`:
   ```yaml
   services:
     postgres:
       image: postgres:18-alpine
       environment:
         POSTGRES_DB: ${POSTGRES_DB:-app}
         POSTGRES_USER: ${POSTGRES_USER:-app}
         POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in .env}
       ports:
         - "127.0.0.1:${POSTGRES_PORT:-5432}:5432"
       volumes:
         - pgdata:/var/lib/postgresql   # PG18 images expect the mount here, not /var/lib/postgresql/data
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
         interval: 5s
         timeout: 3s
         retries: 10
   volumes:
     pgdata:
   ```
3. Create `.env.example` with `POSTGRES_DB=app`, `POSTGRES_USER=app`, `POSTGRES_PASSWORD=change-me`, `POSTGRES_PORT=5432`. Phase 3 appends the `APP_*` keys. Add `.env` to `.gitignore`. Copy it to `.env` locally for dev.
4. Replace `application.properties` with `application.yml`:
   ```yaml
   spring:
     application.name: spring-base
     config.import: optional:file:.env[.properties]
     docker.compose.lifecycle-management: start-only   # keep DB running between app restarts
     jpa:
       open-in-view: false
       hibernate.ddl-auto: validate                   # Flyway owns the schema
       properties.hibernate.jdbc.time_zone: UTC
   management:
     endpoints.web.exposure.include: health,info
     endpoint.health.show-details: never
   ```
5. Tests:
   - In `TestcontainersConfiguration`, pin `postgres:18-alpine` and keep it identical to `compose.yaml`. Add a comment saying the two must change together.
   - Create `support/IntegrationTest.java`, a meta-annotation: `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")`. One shared annotation means one cached Spring context and one container for the whole test run.
   - Create `src/test/resources/application-test.yml`. It is empty for now, except for a comment that phase 3 adds test-only JWT and admin values.
   - Rewrite `ApplicationTests` to use `@IntegrationTest` and assert `GET /actuator/health` → 200 `UP` via `MockMvcTester`.
6. Run `git init` inside `spring-base/` (no commit; commit only when the user asks).
7. Verify (see Success Criteria).

## Success Criteria

- [x] `./mvnw verify` passes (Docker running); the log shows the Testcontainers Postgres 18 container started
- [x] `cp .env.example .env && ./mvnw spring-boot:run` → `docker compose ps` shows postgres `healthy`; `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`
- [x] `git status` shows `.env` untracked/ignored; no `latest` image tags anywhere (`grep -rn ":latest" spring-base` is empty)

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Port 5432 already used by a local Postgres | `POSTGRES_PORT` override in `.env`; bound to 127.0.0.1 only |
| `.env` import is relative to the working dir, so IDE runs from another dir miss it | README: run from project root / set the IDE working dir; app fails fast on missing `${APP_JWT_SECRET}` (phase 3), so the problem is never silent |
| Developer `.env` leaking into tests | `application-test.yml` sets every `app.*` key explicitly; profile-specific values win |
| PG18 volume path differs from older guides | mount at `/var/lib/postgresql` as above; verify the container restarts with data intact |
| First build slow / offline | needs internet once for Maven 3.10.0 + deps |

## Security Considerations

- DB bound to localhost only; dev password lives only in gitignored `.env`
- Actuator exposes only `health` and `info`, with no details
