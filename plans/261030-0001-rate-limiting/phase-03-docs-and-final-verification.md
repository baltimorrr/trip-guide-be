---
phase: 3
title: Docs and final verification
status: completed
priority: P2
dependencies:
  - 2
effort: 1h
---

# Phase 3: Docs and final verification

## Overview

Documents the limits and the proxy requirement, records the decisions, and checks the behaviour in a real local run.

## Context Links

- [plan.md](./plan.md): decisions, defaults, out of scope
- `spring-base/README.md`: Endpoints (error example), Configuration, Production notes, Project structure, Tests,
  Known limitations
- `spring-base/CLAUDE.md`: "Decided" notes at the top, Architecture, Tests, Conventions
- Both files have uncommitted hotel-page edits. Read the current text first and add to it; don't replace it.

## Requirements

- Docs state the real defaults from `application.yml` and the real behaviour verified in phase 2.
- The "no per-IP rate limiting" gap is replaced by the new limits, written as limitations and not as gaps.

## Related Code Files

- Modify: `README.md`
  - **Endpoints:** after the problem+json example, one paragraph: every request is limited per client IP, the five
    limits as a small table, and 429 with `Retry-After`.
  - **Configuration:** rows for `APP_RATE_LIMIT_ENABLED` (`true`) and `APP_RATE_LIMIT_EXEMPT_ADDRESSES` (empty), plus
    one sentence naming `app.rate-limit.limits.*` and `max-tracked-clients`.
  - **Production notes:** rewrite the forward-headers bullet. Behind a reverse proxy, set
    `server.forward-headers-strategy=native`. Without it every client shares the proxy's limits and the whole site
    gets 429s. `native` trusts `X-Forwarded-For` only from private-range proxies (Tomcat `internal-proxies`), so set
    `server.tomcat.remoteip.internal-proxies` if the proxy has a public address. Never expose the app directly while
    trusting forwarded headers.
  - **Project structure:** add `common/ratelimit/`.
  - **Tests:** the test profile exempts `127.0.0.1`, and rate-limit tests send from their own `203.0.113.x`.
  - **Known limitations:** replace "No per-IP rate limiting" with three points:
    - Limits are per instance and in memory: N instances allow N×, and a restart resets them.
    - Keys are per IP, so many users behind one NAT share them.
    - An attacker with many IPs can still lock known accounts.
- Modify: `CLAUDE.md`
  - A **Decided (2026-10-30), rate limiting** note: every request, per IP (IPv6 /64), in memory, stricter limits on
    sign-in, registration, email-sending and search.
  - Architecture: a short **Rate limiting** paragraph:
    - `RateLimitFilter` sits after `CorsFilter` in the security chain and is built in `SecurityConfig`, not a bean.
    - `RateLimitPolicy` holds the matchers; limits are `app.rate-limit.*`.
    - 429s go through `GlobalExceptionHandler`.
    - A new sensitive or expensive endpoint gets a policy matcher.
  - Tests: `127.0.0.1` is exempt in the test profile, so a rate-limit test must send from its own address.
  - Conventions: update the "Known limitations" summary line to match the README.
- `USER_STORIES.md`: leave unchanged unless it marks progress on non-functional lines. Discount-code limiting is still
  open there.

## Implementation Steps

1. Read the current README and CLAUDE.md sections listed above, then make the edits.
2. Check every number in the docs against `application.yml` and every path against `RateLimitPolicy`.
3. Local smoke run (port 8081 / DB 5434 from `.env`; 8080 and 5432 are taken on this machine):
   - `./mvnw spring-boot:run` (the local profile has no exemptions)
   - 11 × `curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' -d '{}'`
     → ten 400s, then 429; `curl -i` on the 11th shows `Retry-After`
   - With `APP_CORS_ALLOWED_ORIGINS=http://localhost:5173`, a request with `Origin: http://localhost:5173` that gets
     429 also carries `Access-Control-Allow-Origin`
   - `curl localhost:8081/actuator/health` still answers 200 after that
4. Run `./mvnw verify` one last time. Fill the "Implementation Notes" in plan.md: test counts, any deviations, and the
   smoke results.
5. No commits. Tell the user the changes are uncommitted and where the rollback copy is.

## Success Criteria

- [x] README and CLAUDE.md describe the limits, the proxy setting and the test exemption, and match the code.
- [x] The smoke run shows 429 + `Retry-After`, CORS headers on a 429, and health not limited.
- [x] `./mvnw verify` passes.

## Risk Assessment

- **Docs overwriting uncommitted edits:** use targeted Edits, never full-file Writes, on README and CLAUDE.md.
- **Smoke run locks you out locally:** a restart resets the buckets, or set `APP_RATE_LIMIT_EXEMPT_ADDRESSES=127.0.0.1`
  in `.env` for day-to-day work, and say so in the README Configuration row.
