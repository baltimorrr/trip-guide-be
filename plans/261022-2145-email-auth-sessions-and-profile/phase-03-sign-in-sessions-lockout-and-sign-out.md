---
phase: 3
title: Sign-in sessions lockout and sign-out
status: completed
priority: P1
effort: 3h
dependencies:
  - 2
---

# Phase 3: Sign-in sessions lockout and sign-out

## Overview

C-AUTH-02 and C-AUTH-05. Login gets lockout and "remember me", and returns a 15-min access token plus a refresh
token tied to a `user_sessions` row. Refresh rotates the token, and replaying an old one revokes the session.
Every access token carries `sid`, and the JWT decoder rejects it as soon as its session is revoked or expired, or
its user is disabled. The phase adds logout (this device) and logout-all.

## Context

- Phase 1: `UserSession`, `UserSessionRepository` (+ custom), `UserRepository.recordFailedLogin/clearFailedLogins`,
  `OpaqueTokens`, `AuthProperties`. Phase 2: verified check in `login`, `TestAuth.registerVerified`.
- `auth/security/TokenService.java`, `JwtConfig.java`, `AppUserDetailsService.java`,
  `common/config/SecurityConfig.java`, `common/exception/GlobalExceptionHandler.java` (AuthenticationException →
  generic 401; `LockedException` is an `AccountStatusException`, already "Invalid email or password").
- `src/test/.../auth/security/TokenServiceTest.java` calls `config.jwtDecoder(key, properties)` and
  `tokenService.issue(user)` directly; both signatures change.

## Requirements

- Functional: generic 401 for unknown email, wrong password and locked account. After 5 failures within 15 min the
  account locks for 15 min. Refresh returns a new token pair. Logout revokes one session; logout-all revokes all.
  A revoked session's access tokens fail at once.
- Non-functional: refresh tokens stored as SHA-256 only. Lockout counting is atomic. Concurrent refreshes of one
  token are serialized by a row lock. Token values never appear in `toString()` or logs.

## Architecture

```
login(email, pw, rememberMe) ─ AuthServiceImpl (no tx)
  ├─ byte limit exceeded ──────────────► users.recordFailedLogin ─► 401 generic
  ├─ AuthenticationManager.authenticate   (UserDetails.accountLocked → LockedException → 401, pw not checked)
  │     └─ BadCredentialsException ─────► users.recordFailedLogin ─► rethrow 401
  ├─ unverified ─► 403
  ├─ failures/lock set ─► users.clearFailedLogins
  └─ SessionService.open [tx] → new user_sessions row → TokenService.issue(user, sid) → TokenResponse

refresh(raw) [tx, noRollbackFor BusinessException]
  s = findByTokenHashForUpdate(hash)            (current OR previous hash, SELECT … FOR UPDATE)
  none                      → 401
  hash == previous          → s.revoke(now); WARN log; 401      (reuse: commit the revoke, then fail)
  !active or user disabled  → s.revoke(now); 401
  else                      → s.rotate(newHash, now); new access token (same sid) + new refresh token

each authenticated request: NimbusJwtDecoder validators = default(exp, nbf, iss) + SessionTokenValidator
  SessionTokenValidator: sid claim → existsByIdAndRevokedAtIsNullAndExpiresAtAfterAndUserEnabledTrue
```

The session's `expires_at` is fixed at sign-in (1d, or 30d with remember me). Rotation never extends it.

## Related Code Files

- Modify: `.../auth/dto/LoginRequest.java` (`Boolean rememberMe`), `.../auth/dto/TokenResponse.java`
- Create: `.../auth/dto/RefreshTokenRequest.java`, `.../auth/dto/ClientInfo.java`
- Create: `.../auth/services/SessionService.java`, `.../auth/services/impl/SessionServiceImpl.java`
- Create: `.../auth/security/SessionTokenValidator.java`
- Modify: `.../auth/security/TokenService.java`, `JwtConfig.java`, `AppUserDetailsService.java`
- Modify: `.../auth/services/AuthService.java`, `.../auth/services/impl/AuthServiceImpl.java`,
  `.../auth/controller/AuthController.java`
- Modify: `.../common/config/SecurityConfig.java`
- Modify: `src/test/.../auth/security/TokenServiceTest.java`, `src/test/.../auth/controller/AuthControllerTest.java`
  (`loginReturnsVerifiableToken`: new fields)
- Create: `src/test/.../auth/controller/AuthControllerSessionTest.java`,
  `src/test/.../auth/controller/AuthControllerLockoutTest.java`,
  `src/test/.../auth/services/impl/SessionServiceImplTest.java`,
  `src/test/.../auth/security/SessionTokenValidatorTest.java`

## Implementation Steps

1. **DTOs**: `TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
   long refreshExpiresIn)` (seconds), with `toString()` masking both tokens.
   `RefreshTokenRequest(@NotBlank @Size(max = 100) String refreshToken)`, masked. `ClientInfo(String userAgent,
   String ip)` with `static ClientInfo from(HttpServletRequest r)` (`User-Agent` header, `getRemoteAddr()`).
   `LoginRequest` gains `Boolean rememberMe` (null = false), and its `toString` stays masked.
2. **`TokenService`**: `public static final String SESSION_CLAIM = "sid"`. `String issue(User user, Long sessionId)`
   adds `.claim(SESSION_CLAIM, String.valueOf(sessionId))` and returns the JWT string only.
3. **`SessionService`** (+ impl, inject `UserSessionRepository`, `TokenService`, `JwtProperties`, `AuthProperties`):
   - `TokenResponse open(User user, boolean rememberMe, ClientInfo client)` `@Transactional`: raw =
     `OpaqueTokens.generate()`; `saveAndFlush(UserSession.create(user, hash(raw), now + ttl, ua, ip))`; build the
     response (`expiresIn = accessTokenTtl`, `refreshExpiresIn = seconds until session.expiresAt`).
   - `TokenResponse refresh(String raw)` `@Transactional(noRollbackFor = BusinessException.class)`: flow above.
     Error: `BusinessException(UNAUTHORIZED, "Invalid or expired refresh token")`, the same detail for every case.
     A short comment explains why `noRollbackFor` is there: without it, the revoke is rolled back with the 401.
   - `void revoke(String raw)` `@Transactional`: `findActiveByTokenHash`, then `revoke(now)` if present. Silent otherwise.
   - `void revokeAll(Long userId)`, `void revokeAllExcept(Long userId, Long keepSessionId)`: bulk methods.
4. **`SessionTokenValidator implements OAuth2TokenValidator<Jwt>`** (`@Component`): a missing or non-numeric
   `sid` fails, and so does an inactive session. Failure: `OAuth2TokenValidatorResult.failure(new
   OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Session is no longer active", null))`.
5. **`JwtConfig.jwtDecoder(SecretKey, JwtProperties, OAuth2TokenValidator<Jwt> sessionTokenValidator)`**:
   `new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), sessionTokenValidator)`.
   The default validators run first, so expired or foreign tokens never hit the database.
6. **`AppUserDetailsService`**: `.accountLocked(user.isLocked(Instant.now()))`.
7. **`AuthServiceImpl.login(LoginRequest, ClientInfo)`**: the flow above. The lock policy comes from
   `AuthProperties.lockout`: `windowStart = now - window`, `lockUntil = now + duration`.
   `login` stays non-transactional, so `recordFailedLogin` commits in its own transaction before the 401 propagates.
   Add `refresh(String)`, `logout(String)` and `logoutAll(Long userId)`, delegating to `SessionService`.
8. **`AuthController`**: `login(@Valid @RequestBody LoginRequest, HttpServletRequest)`; `POST /refresh` → 200;
   `POST /logout` → 204; `POST /logout-all` → 204, user id from `jwt.getSubject()`, authenticated (no
   `@SecurityRequirements`). The public ones carry `@SecurityRequirements`.
9. **`SecurityConfig`**: one `private static final RequestMatcher PUBLIC_AUTH_ENDPOINTS` (POST
   `/api/auth/{register,login,refresh,logout,verify-email,verify-email/resend,password/forgot,password/reset}`
   via `PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, …)` inside an `OrRequestMatcher`).
   Use it for `permitAll` and in `oauth2.bearerTokenResolver(request -> PUBLIC_AUTH_ENDPOINTS.matches(request)
   ? null : defaultResolver.resolve(request))`, commented: a client with an expired access token must still be
   able to refresh or log out.
10. **`TokenServiceTest`**: `issue(user(1L), 7L)` carries `sid = "7"`. Decoders are built with
    `config.jwtDecoder(key, properties, jwt -> OAuth2TokenValidatorResult.success())`. Add a case where a failing
    session validator makes decode throw `JwtValidationException`.
11. **`AuthControllerSessionTest`** (`@IntegrationTest`, own users via `TestAuth`):
    - login → `expiresIn` 900, refresh token is 43 url-safe chars, `refreshExpiresIn` about 1 day (about 30 days
      with `rememberMe: true`). The `user_sessions` row stores `sha256(token)` (≠ token) plus the `User-Agent`
      and IP. The access token has a `sid` matching the row.
    - refresh → new pair, old refresh → 401. Replaying the old token again revokes the session: the newest refresh
      token → 401 and its access token on `/api/auth/me` → 401.
    - unknown refresh token → 401; expired session (`expires_at` moved to the past with `JdbcClient`) → 401.
    - logout(A) → 204: A's access token and A's refresh both 401, while session B on the same user still works.
      Logout with an unknown token → 204.
    - logout-all with A's token → 204 → A and B both 401.
    - `/refresh` and `/login` with `Authorization: Bearer garbage` still succeed.
    - disabled user (`update users set enabled = false`) → access token and refresh both 401.
12. **`AuthControllerLockoutTest`**:
    - 5 wrong passwords → 401 each. The 6th attempt, with the right password, gets a 401 identical (status + body)
      to a wrong one. After moving `locked_until` to the past, login → 200 and `failed_login_count = 0`.
    - 4 failures, then `first_failed_login_at` moved back 16 min, then 1 failure → count 1, not locked.
    - 3 failures then a success → count 0. A 74-byte password counts as a failure.
13. **Unit tests**: `SessionServiceImplTest` (Mockito) covers that rotate keeps the previous hash; a previous-hash
    match revokes and throws; inactive/disabled → 401. `SessionTokenValidatorTest` covers missing `sid`,
    non-numeric `sid`, inactive session → failure, active → success.

## Success Criteria

- [ ] C-AUTH-02 and C-AUTH-05 acceptance criteria in `plan.md` covered by passing tests.
- [ ] Reuse-detection revoke is committed (integration test proves the newest token dies).
- [ ] `./mvnw verify` green, including every pre-existing test that uses `TestAuth`.

## Risk Assessment

- **Per-request DB lookup**: one PK + join on `users` per authenticated request. That's acceptable at this scale.
  A short cache can be added later, but it would weaken "immediate".
- **Two tabs refreshing at once**: the second request waits on the row lock, then matches the previous hash, so
  the device gets signed out. This is by design (user chose reuse detection). README tells clients to
  single-flight refreshes.
- **Lockout as DoS**: anyone can lock a known email for 15 min. That's inherent to the story. Per-IP rate limiting
  is still out of scope.
- **Timing**: a locked account skips BCrypt and answers faster. It's minor and noted in the README rather than
  padded.
- **`noRollbackFor` breadth**: in `refresh`, the only write before a `BusinessException` is the revoke, so nothing
  else can be half-committed. Keep it that way.
- **Behind a proxy**, `getRemoteAddr()` is the proxy's IP. Note `server.forward-headers-strategy` in README
  production notes; it isn't set here.
- **Rollback**: restore the scratchpad copy.
