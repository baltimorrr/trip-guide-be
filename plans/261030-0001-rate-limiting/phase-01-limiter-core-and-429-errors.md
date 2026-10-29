---
phase: 1
title: Limiter core and 429 errors
status: completed
priority: P2
dependencies: []
effort: 2h
---

# Phase 1: Limiter core and 429 errors

## Overview

Adds the dependencies, the `app.rate-limit` settings, the limits and their request matchers, the in-memory token
buckets, and the 429 error. Nothing is wired into requests yet. Unit tests run without Docker.

## Context Links

- [plan.md](./plan.md): decisions and default numbers
- `src/main/java/com/company/project/auth/config/AuthProperties.java`: style for a `@Validated` properties record
- `src/main/java/com/company/project/common/config/SecurityConfig.java`: `PathPatternRequestMatcher` style
- `src/main/java/com/company/project/common/exception/GlobalExceptionHandler.java`: `problem(...)` helper
- Bucket4j 8.21.0 API (checked against the jar on 2026-10-30): `Bucket.builder().addLimit(limit ->
  limit.capacity(n).refillGreedy(n, period)).withCustomTimePrecision(timeMeter).build()`,
  `bucket.tryConsumeAndReturnRemaining(1)` → `ConsumptionProbe.isConsumed()` / `getNanosToWaitForRefill()`,
  `TimeMeter { long currentTimeNanos(); boolean isWallClockBased(); }`, `TimeMeter.SYSTEM_NANOTIME`

## Requirements

- Functional: a limit allows `capacity` requests at once and refills `capacity` per `period` gradually. Each
  (limit, client) pair has its own bucket. A rejected try reports the whole seconds until one more request is
  allowed (rounded up, at least 1).
- Functional: client key = the address as given for IPv4. IPv6 → its first 64 bits. An IPv4-mapped IPv6 address
  becomes the IPv4 address. Anything unparseable is used as is. Never a DNS lookup.
- Non-functional: memory is bounded. Each limit keeps at most `max-tracked-clients` buckets. A bucket idle for its
  limit's `period` is dropped; it would be full again by then anyway, so nothing is lost.
- Non-functional: missing or invalid settings fail startup with a report naming the property, like `app.jwt`.

## Architecture

New package `com.company.project.common.ratelimit` (imports no feature package; endpoint paths are strings, as in
`SecurityConfig`).

- `RateLimitProperties`: `@Validated @ConfigurationProperties("app.rate-limit")` record:
  - `boolean enabled`
  - `@NotNull List<String> exemptAddresses`
  - `@Min(1) int maxTrackedClients`
  - `@Valid @NotNull Limits limits`
  - nested `Limits(general, login, register, email, search)`, each `@Valid @NotNull Limit`
  - nested `Limit(@Min(1) long capacity, @NotNull Duration period)`, compact constructor rejects a non-positive
    period
- `RateLimitPolicy` enum: `GENERAL, LOGIN, REGISTER, EMAIL, SEARCH`. Each constant has a `RequestMatcher`
  (`GENERAL` = `AnyRequestMatcher.INSTANCE`, others `PathPatternRequestMatcher.withDefaults().matcher(method,
  path)`, `OrRequestMatcher` for the shared buckets). `limit(Limits)` picks its `Limit` (switch). A static
  `matching(HttpServletRequest)` returns the policies for a request, `GENERAL` first.
- `RateLimiter` (plain class, not a bean): `EnumMap<RateLimitPolicy, Cache<String, Bucket>>`, one Caffeine cache per
  policy (`maximumSize(maxTrackedClients)`, `expireAfterAccess(limit.period())`).
  - `public RateLimiter(RateLimitProperties)` delegates to package-private `RateLimiter(RateLimitProperties,
    TimeMeter)`, which tests call with a fake clock.
  - `OptionalLong secondsToWait(RateLimitPolicy, String client)`: empty when the request may go ahead.
  - `static String clientKey(String remoteAddress)`.
- `common/exception/RateLimitExceededException extends RuntimeException`, carrying `long retryAfterSeconds`. It is
  not a `BusinessException`, because its handler must add a header.
- `GlobalExceptionHandler.handleRateLimit` → `ResponseEntity<ProblemDetail>` with status 429, `Retry-After`, detail
  `"Too many requests, retry later"`.

## Related Code Files

- Modify: `pom.xml`: property `bucket4j.version` 8.21.0, `com.bucket4j:bucket4j_jdk17-core:${bucket4j.version}`, and
  `com.github.ben-manes.caffeine:caffeine` (version managed by Spring Boot, 3.2.4). Don't add
  `bucket4j_jdk17-caffeine`: it is built against Caffeine 2.9.3 (`provided`), and our own cache needs none of it.
- Modify: `src/main/resources/application.yml`: `app.rate-limit` block (below)
- Modify: `src/test/resources/application-test.yml`: `app.rate-limit.exempt-addresses: 127.0.0.1`
- Modify: `src/main/java/com/company/project/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/company/project/common/ratelimit/RateLimitProperties.java`
- Create: `src/main/java/com/company/project/common/ratelimit/RateLimitPolicy.java`
- Create: `src/main/java/com/company/project/common/ratelimit/RateLimiter.java`
- Create: `src/main/java/com/company/project/common/exception/RateLimitExceededException.java`
- Create: `src/test/java/com/company/project/common/ratelimit/RateLimiterTest.java`

```yaml
app:
  rate-limit:
    # Every request is limited per client IP (an IPv6 client by its /64). Behind a reverse proxy set
    # server.forward-headers-strategy, or all clients share the proxy's limits.
    enabled: ${APP_RATE_LIMIT_ENABLED:true}
    # Comma-separated addresses that are never limited, e.g. an internal monitor. Empty = none.
    exempt-addresses: ${APP_RATE_LIMIT_EXEMPT_ADDRESSES:}
    # Most clients remembered per limit; past that the least recently seen are forgotten.
    max-tracked-clients: 50000
    limits:
      # capacity requests per period, refilled gradually. A request under a specific limit also spends one of general.
      general:
        capacity: 300
        period: 1m
      login:
        capacity: 10
        period: 1m
      register:
        capacity: 10
        period: 1h
      # verify-email/resend and password/forgot share this one.
      email:
        capacity: 10
        period: 1h
      # search/hotels, search/hotels/filters and hotels/{slug}/rooms share this one.
      search:
        capacity: 60
        period: 1m
```

## Implementation Steps

1. Back up `spring-base/` (without `target/`) to the scratchpad (see plan.md Rollback).
2. Add the two dependencies and `bucket4j.version`. Run `./mvnw -q dependency:tree -Dincludes=com.github.ben-manes.caffeine`
   to confirm one Caffeine version (3.2.4).
3. Write `RateLimitProperties`, then the YAML in both profiles. Keep the comment style of the existing `app.*` blocks.
4. Write `RateLimitPolicy` with the matchers from the plan.md table. Use `/api/hotels/*/rooms` for the room list.
5. Write `RateLimiter`:
   - New bucket: `Bucket.builder().addLimit(l -> l.capacity(c).refillGreedy(c, period)).withCustomTimePrecision(time).build()`.
   - Wait in seconds: `Math.max(1, ceil(nanosToWait / 1e9))`.
   - `clientKey`: `InetAddress.getByName` only when the text is an IP literal (contains `:` or is dotted digits).
     Inet6 → zero the last 8 bytes and format it. Inet4 (including mapped) → `getHostAddress()`. On a parse failure,
     return the input.
6. Add `RateLimitExceededException` and its handler. Put the handler next to the other specific handlers, above the
   generic `Exception` one.
7. Write `RateLimiterTest` (plain JUnit + AssertJ, a fake `TimeMeter` with a settable `nanos` field):
   - `capacity` tries pass, the next is rejected with the expected wait (`login` 10/min → 6 s)
   - after advancing the clock by one refill interval exactly one more try passes
   - two clients, and two policies for one client, don't share buckets
   - the wait rounds up and is never 0
   - `clientKey`: IPv4 unchanged; two addresses in one /64 give one key; different /64s differ;
     `::ffff:203.0.113.7` → `203.0.113.7`; garbage is returned unchanged
8. Run `./mvnw test -Dtest=RateLimiterTest` (no Docker), then `./mvnw -q compile`.

## Success Criteria

- [x] `RateLimiterTest` passes without Docker.
- [x] The app context starts (any existing `@IntegrationTest`, e.g. `./mvnw test -Dtest=AuthControllerTest`) with the
      new properties bound and validated.
- [x] One Caffeine version on the classpath, and no `bucket4j_jdk17-caffeine`.

## Risk Assessment

- **API drift:** the method names above were checked against the 8.21.0 jar. If a call doesn't compile, check
  `javap` on the jar before changing the approach.
- **Memory:** 5 limits × 50,000 buckets, about 300 bytes each, is roughly 75 MB at worst. Under a key spray, the least
  recently seen clients are dropped. That fails open for them: they start again with a full bucket. Lower
  `max-tracked-clients` for small heaps.
- **Rounding:** `Retry-After` must never be 0, because clients would retry at once. The test covers it.
