---
phase: 2
title: Request filter and integration tests
status: completed
priority: P2
dependencies:
  - 1
effort: 2h
---

# Phase 2: Request filter and integration tests

## Overview

Checks every request against its limits in one filter inside the security chain, right after CORS, and proves the
behaviour end to end with an `@IntegrationTest`.

## Context Links

- [plan.md](./plan.md), [phase 1](./phase-01-limiter-core-and-429-errors.md)
- `src/main/java/com/company/project/common/exception/ProblemDetailSecurityHandler.java`: hands an exception to
  `HandlerExceptionResolver` from inside the filter chain, so `GlobalExceptionHandler` renders it. The new filter
  does the same.
- `src/test/java/com/company/project/support/IntegrationTest.java`, `TestAuth.java`: test conventions
- `src/test/java/com/company/project/auth/controller/AuthControllerSessionTest.java:62`: asserts the stored session
  IP is `127.0.0.1`, which is why tests keep MockMvc's default address (exempt) and don't randomise it

## Key Insights

- **Position: after Spring Security's `CorsFilter`, before authentication.**
  - In front of Spring Security, a 429 would leave without `Access-Control-Allow-Origin`, and browser code couldn't
    read the status or `Retry-After`.
  - After `CorsFilter`, valid preflights are answered before they reach the limiter, so they aren't counted.
  - Before `BearerTokenAuthenticationFilter`, a flood of bad tokens is limited before JWT verification and the
    session lookup.
  - The chain has no `securityMatcher`, so every request passes through it.
- **The filter isn't a bean.** `SecurityConfig` builds it with `new` and calls `addFilterAfter(…, CorsFilter.class)`.
  As a `@Component`, Boot would also register it in the servlet container and it would run twice per request.
- **The body is never read.** The filter runs before MVC, so invalid JSON or failed validation still spends a token,
  and sign-in is rejected before BCrypt runs.
- **Buckets live as long as the shared test context.** Each test sends from its own documentation address
  (`203.0.113.0/24`) through a `RequestPostProcessor` that sets `remoteAddr`.

## Requirements

- Functional: for each matching policy (`GENERAL` first), ask `RateLimiter.secondsToWait`. On the first rejection,
  resolve `RateLimitExceededException` through the `handlerExceptionResolver` and stop the chain. Otherwise continue.
- Functional: `/actuator/health/**` is never limited (`shouldNotFilter`). A 429 health probe would take a healthy
  instance out of the load balancer.
- Functional: exempt addresses are normalised with `clientKey`, so an IPv6 entry covers its /64.
- Functional: `app.rate-limit.enabled: false` → the filter isn't added.
- Non-functional: rejections are logged at DEBUG only (policy + client key), because an attack would flood an INFO
  log.

## Architecture

```
request ─▶ … HeaderWriterFilter ─▶ CorsFilter ─▶ RateLimitFilter ─▶ BearerTokenAuthenticationFilter ─▶ … ─▶ controller
                                                   │ over a limit
                                                   ▼
                                handlerExceptionResolver ─▶ GlobalExceptionHandler.handleRateLimit ─▶ 429 + Retry-After
```

`RateLimitFilter extends OncePerRequestFilter`:

- constructor `(RateLimitProperties, HandlerExceptionResolver)`: builds `new RateLimiter(properties)` and the
  normalised exempt set
- `shouldNotFilter`: the health matcher
- `doFilterInternal`: as in Requirements

`OncePerRequestFilter` skips error dispatches by default, so the `/error` forward isn't counted twice.

## Related Code Files

- Create: `src/main/java/com/company/project/common/ratelimit/RateLimitFilter.java`
- Modify: `src/main/java/com/company/project/common/config/SecurityConfig.java`
  - inject `RateLimitProperties` and `@Qualifier("handlerExceptionResolver") HandlerExceptionResolver` into
    `securityFilterChain`
  - `if (rateLimits.enabled()) http.addFilterAfter(new RateLimitFilter(rateLimits, resolver), CorsFilter.class);`
    with a short comment on why that position
  - This file has uncommitted hotel-page changes. Edit only the method signature and that one line.
- Create: `src/test/java/com/company/project/common/ratelimit/RateLimitFilterTest.java` (`@IntegrationTest`)

## Implementation Steps

1. Write `RateLimitFilter`. Health matcher: `PathPatternRequestMatcher.withDefaults().matcher("/actuator/health/**")`.
   Check that the pattern also matches `/actuator/health` itself; if it doesn't, add that path too.
2. Wire it in `SecurityConfig` (the `CorsFilter` class is `org.springframework.web.filter.CorsFilter`).
3. Write `RateLimitFilterTest` with `MockMvcTester`, a static `AtomicInteger`, and a `from()` helper that returns a
   fresh `203.0.113.N` post-processor. Tests (cheap requests, so a 300-request loop takes seconds):
   - `loginIsLimitedBeforeTheBodyIsRead`: 10 × `POST /api/auth/login` with `{}` → 400. The 11th → 429 with
     `application/problem+json`, `status` 429, `detail` "Too many requests, retry later", and a numeric
     `Retry-After` between 1 and 60.
   - `anotherAddressIsNotLimited`: exhaust `login` on address A, then address B gets 400, not 429.
   - `resendAndForgotShareOneLimit`: 5 × resend + 5 × forgot with unknown `TestAuth.uniqueEmail()`s → 202 each. The
     11th, either endpoint → 429.
   - `registrationIsLimited`: 10 × `POST /api/auth/register` with `{}` → 400. The 11th → 429.
   - `searchEndpointsShareOneLimit`: 60 requests alternating `GET /api/search/hotels` (no parameters → 400) and
     `GET /api/hotels/no-such-hotel-<uuid>/rooms` (400 or 404). The 61st → 429.
   - `everyRequestCountsTowardTheGeneralLimit`: 300 × `GET /api/auth/me` with no token → 401. The 301st → 429.
     After that, from the same address, `POST /api/auth/login` → 429 even though its own bucket is unused, and
     `GET /actuator/health` → 200.
4. Run `./mvnw test -Dtest=RateLimitFilterTest`, then the full `./mvnw verify` (Docker running). Every existing test
   must pass unchanged. If one gets a 429, it is sending from a non-exempt address. Fix the test setup, never the
   limits.

## Success Criteria

- [x] All `RateLimitFilterTest` cases pass.
- [x] `./mvnw verify` passes with no change to existing tests. Record the test count before and after.
- [x] `RateLimitFilter` runs once per request: it isn't a bean, and there's no `FilterRegistrationBean`.

## Risk Assessment

- **Shared context state:** buckets persist across test classes in one run. Unique addresses per test avoid
  interference, and assertions only use the test's own address.
- **Resolver outside the DispatcherServlet:** the pattern is proven by `ProblemDetailSecurityHandler`. If the body
  comes back empty, compare with how that class is called.
- **Matcher drift:** a renamed endpoint silently loses its specific limit and keeps only `general`. The integration
  tests catch the five covered paths.
