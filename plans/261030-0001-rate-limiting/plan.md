---
title: Rate limiting
description: >-
  Every request is rate limited per client IP (IPv6 by /64), with stricter
  limits on sign-in, registration, the email-sending endpoints and the priced
  search. Token buckets in memory (Bucket4j core + Caffeine), checked by one
  filter inside the security chain right after CORS. Over the limit: 429
  problem+json with Retry-After.
status: completed
priority: P2
tags:
  - backend
  - security
  - api
blockedBy: []
blocks: []
created: 2026-10-30T00:00:00.000Z
---

# Rate limiting

## Overview

Sources: README "Known limitations" ("No per-IP rate limiting … Add a limiter (e.g. Bucket4j or at the gateway)"),
`USER_STORIES.md` non-functional security ("rate limiting on sign-in and discount code checks"; discount codes don't
exist yet). Today only per-account protection exists: lockout (5 failures / 15 min) and the 1-minute email
cooldown. Nothing stops one client spraying many emails at sign-in, locking many accounts, triggering reset emails
for thousands of addresses, or looping the priced search SQL.

All other plans in `plans/` are completed, so nothing blocks this plan and it blocks nothing.

## User Decisions (2026-10-30, do not reverse silently)

| Decision | Choice |
|----------|--------|
| Scope | **Every endpoint** is limited, with stricter limits on the sensitive and expensive ones |
| Storage | **In memory per instance** (Bucket4j + Caffeine). No table. Counts reset on restart; N instances allow N× |
| Git | No commits or staging by Claude (standing preference) |

## Planner Defaults (changeable)

| Limit | Requests | Per client IP |
|-------|----------|---------------|
| `general` | every request except `/actuator/health/**` and CORS preflights | 300 / 1 min |
| `login` | `POST /api/auth/login` | 10 / 1 min |
| `register` | `POST /api/auth/register` | 10 / 1 h |
| `email` | `POST /api/auth/verify-email/resend` and `/api/auth/password/forgot`, one shared bucket | 10 / 1 h |
| `search` | `GET /api/search/hotels`, `/api/search/hotels/filters`, `/api/hotels/{slug}/rooms`, one shared bucket | 60 / 1 min |

- A request under a specific limit also spends one from `general`. Buckets refill gradually (greedy): `login` allows
  a burst of 10, then one every 6 s.
- The client is `request.getRemoteAddr()`. An IPv6 client is grouped by its /64, since one host usually owns a whole
  /64. Signed-in requests are keyed by IP too, not by user.
- Over the limit: `429` problem+json `"Too many requests, retry later"` with `Retry-After` (whole seconds, ≥ 1). The
  request is rejected before authentication, body parsing and validation.
- `app.rate-limit.enabled` (env `APP_RATE_LIMIT_ENABLED`, default `true`) turns it off when a gateway limits instead.
  `app.rate-limit.exempt-addresses` (env `APP_RATE_LIMIT_EXEMPT_ADDRESSES`, empty by default) are never limited. The
  test profile exempts `127.0.0.1`, MockMvc's address, so the rest of the suite is unaffected.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Limiter core and 429 errors](./phase-01-limiter-core-and-429-errors.md) | Completed |
| 2 | [Request filter and integration tests](./phase-02-request-filter-and-integration-tests.md) | Completed |
| 3 | [Docs and final verification](./phase-03-docs-and-final-verification.md) | Completed |

Each phase depends on the one before it.

## Acceptance Criteria

- [x] The 11th `POST /api/auth/login` from one address within a minute gets 429 problem+json with `Retry-After`,
      even when the body is invalid. Another address is unaffected.
- [x] `register`, `email` (resend + forgot share one bucket) and `search` (three endpoints share one bucket) enforce
      their limits. Every other request, including ones that end in 401, counts toward `general`.
- [x] `/actuator/health` is never limited. A 429 carries the CORS headers a normal response from that origin gets.
- [x] Memory is bounded (`max-tracked-clients` per limit). No migration and no new table.
- [x] Existing tests pass unchanged. `./mvnw verify` passes. README and CLAUDE.md are updated.

## Out of Scope

Shared counters (PostgreSQL/Redis) across instances, per-user keys for signed-in requests, `RateLimit-*` or
`X-RateLimit-*` headers on every response, CIDR ranges in the exempt list, discount-code limits (no discount codes
yet), stopping account lockout from many IPs (needs CAPTCHA or progressive delays), limits at a reverse proxy.

## Open Questions

None blocking. The default numbers are guesses. Check them against real traffic before launch, especially `general`
for many users behind one NAT (offices, mobile carriers).

## Rollback

No commits. **Before phase 1**, ideally commit the uncommitted hotel-page work (C-HOTEL) yourself, so `git status`
shows only this plan's changes. Either way, back up `spring-base/` (without `target/`) to the session scratchpad as
`rollback/spring-base-before-rate-limiting.tgz`. There is no migration. To undo, delete `common/ratelimit/`,
`RateLimitExceededException` and the two tests, and revert `pom.xml`, `GlobalExceptionHandler`, `SecurityConfig`,
both YAML files, README and CLAUDE.md.

## Implementation Notes (2026-10-30)

- Done as planned. Bucket4j 8.21.0 core + Caffeine 3.2.4 (Boot-managed), no `bucket4j_jdk17-caffeine`. The filter
  is built in `SecurityConfig` and added after `CorsFilter`. It isn't a bean and there's no `FilterRegistrationBean`.
  `RateLimiter` is package-private: only the filter, in the same package, builds it.
- `./mvnw verify` passed: 282 tests, 0 failures (268 before). The only new ones are `RateLimiterTest` (8, no Docker)
  and `RateLimitFilterTest` (6). No existing test changed. The test profile only adds `exempt-addresses: 127.0.0.1`.
- Deviation in `RateLimitFilterTest`: the `general` and `search` tests first expected the 429 at exactly capacity + 1
  and failed. `general` refills one request every 200 ms and `search` one a second, so about 9 and 2 came back while
  the loops ran (about 1.8 s). Those two tests now send until the first 429 and assert it comes after the capacity
  but before the bound that separate or missing buckets would allow (shared search bucket: under 120 of a possible
  180). `login`, `register` and `email` keep exact counts, since they refill every 6 s or slower.
- `/actuator/health/**` also matches `/actuator/health`, checked by the test and the smoke run.
- Beyond the phase files: README's "Adding a feature" checklist now says expensive or abusable endpoints get a
  `RateLimitPolicy` matcher. `USER_STORIES.md` is unchanged, since its non-functional lines don't track progress.
- Local smoke run on **port 8082**. An older app process (pid 263265, started 2026-10-28 20:05, not started in this
  session) holds 8081, so it was left running and the new build ran beside it:
  - 10 × `POST /api/auth/login {}` → 400; the 11th → 429, `Retry-After: 6`, `Access-Control-Allow-Origin:
    http://localhost:5173`, problem+json body
  - preflight `OPTIONS /api/auth/login` → 200 with CORS headers (not counted)
  - `/actuator/health` → 200, and `/api/search/suggestions` → 200 from the same address
  - The empty `APP_RATE_LIMIT_EXEMPT_ADDRESSES` default binds to an empty list (the local run limited 127.0.0.1).
- No commits. Rollback copy: `rollback/spring-base-before-rate-limiting.tgz` in the session scratchpad. The
  hotel-page work was still uncommitted when this started, so `git status` shows both.
