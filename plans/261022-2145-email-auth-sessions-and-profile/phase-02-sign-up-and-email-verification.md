---
phase: 2
title: Sign-up and email verification
status: completed
priority: P1
effort: 2h
dependencies:
  - 1
---

# Phase 2: Sign-up and email verification

## Overview

C-AUTH-01: registration takes confirm password and optional phone, enforces the strength rule, starts the account
unverified and logs a verification link. Verify and resend endpoints. Login refuses unverified accounts (403)
once the password is right. `TestAuth` verifies through the recording mailer so the rest of the suite keeps working.

## Context

- Phase 1 outputs: `UserToken`, `UserTokenRepository`, `OpaqueTokens`, `AuthProperties`, `AuthMailer`,
  `@StrongPassword`, `@PhoneNumber`, `RecordingAuthMailer`.
- `auth/controller/AuthController.java`, `auth/services/impl/AuthServiceImpl.java`, `auth/dto/RegisterRequest.java`,
  `user/services/UserService.java` + impl, `user/bootstrap/AdminBootstrap.java`, `user/dto/UserResponse.java`,
  `user/mapper/UserMapper.java`, `common/config/SecurityConfig.java`.
- Tests: `support/TestAuth.java`, `auth/controller/AuthControllerTest.java`, `user/services/impl/UserServiceImplTest.java`,
  `user/bootstrap/AdminBootstrapTest.java`.

## Requirements

- Functional: fullName, email, password, confirmPassword required; phone optional; weak or mismatched password and
  duplicate email give clear errors; the account can sign in only after its link is used.
- Non-functional: link is single use, expires (24h), and only its hash is stored; resend never reveals whether an
  email is registered.

## Architecture

```
POST /register ─► AuthService.register (no tx)
                   ├─ UserService.create(..., emailVerified=false)        [tx 1]
                   ├─ UserTokenService.issue(user, EMAIL_VERIFY) → raw    [tx 2]
                   └─ AuthMailer.sendEmailVerification(email, link)       (logs)
POST /verify-email {token} ─► AuthService.verifyEmail [tx]: UserTokenService.consume → user.verifyEmail(now)
POST /verify-email/resend {email} ─► 202; issue + mail only if user exists, enabled, unverified, not in cooldown
```

A failure between tx 1 and tx 2 leaves an unverified user without a link; resend recovers it. Mail is sent after the
token commits, never inside a transaction.

## Related Code Files

- Modify: `.../auth/dto/RegisterRequest.java`, `.../auth/controller/AuthController.java`,
  `.../auth/services/AuthService.java`, `.../auth/services/impl/AuthServiceImpl.java`
- Create: `.../auth/dto/VerifyEmailRequest.java`, `.../auth/dto/EmailRequest.java`
- Create: `.../auth/services/UserTokenService.java`, `.../auth/services/impl/UserTokenServiceImpl.java`
- Modify: `.../auth/config/AuthProperties.java` (add `link(String path, String token)`)
- Modify: `.../user/services/UserService.java`, `.../user/services/impl/UserServiceImpl.java`,
  `.../user/bootstrap/AdminBootstrap.java`, `.../user/dto/UserResponse.java`, `.../user/mapper/UserMapper.java`
- Modify: `.../common/config/SecurityConfig.java`
- Modify: `src/test/.../support/TestAuth.java`, `src/test/.../auth/controller/AuthControllerTest.java`,
  `src/test/.../user/services/impl/UserServiceImplTest.java`, `src/test/.../user/bootstrap/AdminBootstrapTest.java`
- Create: `src/test/.../auth/services/impl/UserTokenServiceImplTest.java`

## Implementation Steps

1. **`RegisterRequest`**:
   ```java
   public record RegisterRequest(
       @NotBlank @Email @Size(max = 254) String email,
       @NotBlank @StrongPassword String password,
       @NotBlank String confirmPassword,
       @NotBlank @Size(max = 100) String fullName,
       @PhoneNumber String phone) {

     // Reported as field "passwordConfirmed"; null password is left to @NotBlank.
     @JsonIgnore
     @AssertTrue(message = "must match password")
     public boolean isPasswordConfirmed() { return password == null || password.equals(confirmPassword); }

     @Override public String toString() { /* mask password and confirmPassword */ }
   }
   ```
2. **`UserService.create(email, rawPassword, fullName, phone, roles, boolean emailVerified)`**: verified →
   `emailVerifiedAt = now`. Add `void changePassword(User user, String rawPassword)` now (byte-limit check →
   400, encode, `user.changePasswordHash`), used by phase 4. `AdminBootstrap` passes `null` phone and `true`.
3. **`UserResponse`** → `(Long id, String email, String fullName, String phone, String countryCode,
   String preferredCurrency, boolean emailVerified, List<Role> roles, Instant createdAt)`; update `UserMapper`.
4. **`AuthProperties.link(path, token)`**: `UriComponentsBuilder.fromUriString(frontendUrl).path(path)
   .queryParam("token", token).build().toUriString()`. Paths `/verify-email` and `/reset-password` as constants
   where they are used.
5. **`UserTokenService`** (+ impl, `@Transactional` methods):
   - `Optional<String> issue(User user, TokenType type)`: empty when `emailCooldown > 0` and
     `existsByUserIdAndTypeAndCreatedAtAfter(id, type, now - cooldown)`; else `invalidateUnused`, generate,
     save hash with `expiresAt = now + ttl(type)` (`emailVerificationTtl` / `passwordResetTtl`), return raw.
   - `User consume(String rawToken, TokenType type)`: `findByTokenHashForUpdate(hash(raw))`; wrong type, used or
     expired → `BusinessException(BAD_REQUEST, "Invalid or expired link")`; else `markUsed(now)`, return
     `token.getUser()`. Javadoc: callers must hold a transaction (the user is a lazy reference).
6. **`AuthService`**: `register` as in Architecture; `void verifyEmail(String token)` (`@Transactional`);
   `void resendVerification(String email)` (normalize; `findByEmail`; skip silently unless enabled and unverified).
   In `login`, after the user is loaded: `!user.isEmailVerified()` → `BusinessException(FORBIDDEN,
   "Email not verified")`. Wrong password still fails earlier with the generic 401, so the 403 reveals nothing
   to someone without the password.
7. **`AuthController`**: `POST /verify-email` (`@ResponseStatus(NO_CONTENT)`), `POST /verify-email/resend`
   (`ACCEPTED`), both `@SecurityRequirements`. `VerifyEmailRequest(@NotBlank @Size(max = 100) String token)` masks
   the token in `toString()`; `EmailRequest(@NotBlank @Email @Size(max = 254) String email)`.
8. **`SecurityConfig`**: add the two paths to the public POST matcher (phase 3 turns this list into one shared
   `RequestMatcher`).
9. **`TestAuth`**: `register` JSON adds `"confirmPassword"` = password. New `registerVerified(mvc, email, password)`:
   register (201) → `RecordingAuthMailer.verificationToken(email)` → `POST /api/auth/verify-email` (204).
   `userToken(mvc)` uses it. Grep `src/test` for other direct `register(`+`login(` pairs and switch them.
10. **Tests** (`AuthControllerTest`, test names describe behavior):
    - weak passwords (`abcdefgh`, `12345678`, `short1`) → 400 on `password`; mismatch → 400 on
      `passwordConfirmed`; missing confirm → 400; duplicate email (any case) → 409 "Email already registered".
    - register → 201 with `emailVerified: false`, phone trimmed; recording mailer has a link under
      `http://localhost:5173/verify-email?token=`.
    - right password before verifying → 403 "Email not verified"; wrong password → 401 generic; after verify → 200.
    - token used twice → 400; expired (`update user_tokens set expires_at = now() - interval '1 minute'` via
      `JdbcClient`) → 400; garbage token → 400.
    - resend: old link → 400, new link → 204; unknown email → 202, no link recorded; verified user → 202, no new link.
    - existing `passwordOverBcryptByteLimitIsRejected`: use `"é".repeat(36) + "1"` (73 bytes, passes the pattern) so
      the 400 still comes from the byte limit; `invalidRegistrationReturnsFieldErrors`: add `confirmPassword`.
    - `UserTokenServiceImplTest` (Mockito): cooldown → empty, nothing saved; issue invalidates older tokens and stores
      a 64-char hash, never the raw token; consume rejects wrong type / used / expired.
    - `UserServiceImplTest`, `AdminBootstrapTest`: new `create` signature; bootstrap admin created verified.

## Success Criteria

- [ ] All C-AUTH-01 acceptance criteria in `plan.md` covered by passing tests.
- [ ] Every existing integration test passes through `TestAuth.registerVerified`.
- [ ] Server log (local run) shows exactly one verification link per register/resend.

## Risk Assessment

- `@AssertTrue` on a record method: Hibernate Validator treats `isPasswordConfirmed()` as a getter (field
  `passwordConfirmed`); `@JsonIgnore` keeps Jackson from treating it as a property. If either misbehaves, fall back
  to a class-level constraint that reports on `confirmPassword`.
- Registration still answers 409 for a taken email (story requires a clear message). An attacker can pre-register a
  victim's email unverified; the victim recovers it via forgot password (phase 4 marks the email verified on reset).
- Changing `UserResponse`'s record shape is additive for JSON clients; only `UserMapper` constructs it.
