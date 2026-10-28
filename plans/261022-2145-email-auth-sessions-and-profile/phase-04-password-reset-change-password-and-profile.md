---
phase: 4
title: Password reset change password and profile
status: completed
priority: P1
effort: 2h
dependencies:
  - 3
---

# Phase 4: Password reset change password and profile

## Overview

This phase covers two stories:

- **C-AUTH-04, forgot password and reset.** Forgot password logs a reset link that lasts 30 minutes and works
  once. The reset sets the new password and revokes every session.
- **C-AUTH-06, profile.** `GET/PUT /api/auth/me` reads and edits name, phone, nationality and preferred currency.
  `PUT /api/auth/me/password` changes the password and revokes the other sessions.

## Context

- Phase 1: `@IsoCountry`, `@IsoCurrency`, `@PhoneNumber`, `@StrongPassword`, `User.updateProfile`,
  `User.changePasswordHash`.
- Phase 2: `UserTokenService.issue/consume`, `UserService.changePassword`, `AuthProperties.link`,
  `RecordingAuthMailer.resetToken`.
- Phase 3: `SessionService.revokeAll/revokeAllExcept`, the `sid` claim (`TokenService.SESSION_CLAIM`), and the
  password paths already in `PUBLIC_AUTH_ENDPOINTS`.
- `auth/controller/AuthController.java` currently holds `GET /me`, and `AuthService.currentUser` backs it.

## Requirements

- Forgot password answers the same for every email, and only enabled accounts get a link.
- A reset link works once, expires after 30 minutes, and only the newest one works.
- A successful reset signs out every device, clears the lockout and marks the email verified.
- Change password needs the current password, and the new one must pass the strength rule. It revokes every
  session except the caller's.
- Profile PUT replaces all four fields: an optional field that is missing is cleared. Codes are stored uppercase.
  An invalid code gets a 400 that names the field.

## Architecture

```
POST /password/forgot {email} ─► 202; PasswordService.forgotPassword
                                  user exists && enabled → UserTokenService.issue(PASSWORD_RESET)
                                  → AuthMailer.sendPasswordReset(email, link /reset-password?token=)
POST /password/reset {token, newPassword} ─► PasswordService.resetPassword [tx]
     consume(token, PASSWORD_RESET) → user enabled? → UserService.changePassword → user.verifyEmail(now)
     → SessionService.revokeAll(userId)                                                → 204
PUT /me/password {currentPassword, newPassword} ─► PasswordService.changePassword(userId, sid, req) [tx]
     matches(current)? else 400 → UserService.changePassword → revokeAllExcept(userId, sid) → 204
GET/PUT /me ─► ProfileService (user/) → UserResponse
```

`ProfileController` lives in `auth/controller`. It sits on the existing `/api/auth/me` path and uses
`PasswordService`. Putting it in `user/` would make `user` import `auth` and create a package cycle.

## Related Code Files

- Create: `.../auth/dto/ResetPasswordRequest.java`, `.../auth/dto/ChangePasswordRequest.java`
- Create: `.../auth/services/PasswordService.java`, `.../auth/services/impl/PasswordServiceImpl.java`
- Create: `.../auth/controller/ProfileController.java`
- Modify: `.../auth/controller/AuthController.java` (add the forgot/reset endpoints, remove `GET /me`)
- Modify: `.../auth/services/AuthService.java` + impl (remove `currentUser`)
- Create: `.../user/dto/ProfileUpdateRequest.java`
- Create: `.../user/services/ProfileService.java`, `.../user/services/impl/ProfileServiceImpl.java`
- Create: `src/test/.../auth/controller/AuthControllerPasswordTest.java`, `src/test/.../auth/controller/ProfileControllerTest.java`
  (move the existing `/me` tests here from `AuthControllerTest`)
- Create: `src/test/.../auth/services/impl/PasswordServiceImplTest.java`, `src/test/.../user/services/impl/ProfileServiceImplTest.java`

## Implementation Steps

1. **DTOs**:
   - `ResetPasswordRequest(@NotBlank @Size(max = 100) String token, @NotBlank @StrongPassword String newPassword)`
   - `ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @StrongPassword String newPassword)`
   - `ProfileUpdateRequest(@NotBlank @Size(max = 100) String fullName, @PhoneNumber String phone,
     @IsoCountry String countryCode, @IsoCurrency String preferredCurrency)`

   The first two mask their secrets in `toString()`.
2. **`PasswordService`** (+ impl): the three methods in Architecture.
   - Errors: an invalid link and a disabled user both get `BusinessException(BAD_REQUEST, "Invalid or expired
     link")`.
   - A wrong current password gets `BusinessException(BAD_REQUEST, "Current password is incorrect")`. A current
     password over 72 bytes counts as wrong, because `matches` would reject it instead of answering "no match".
   - `forgotPassword` has no transaction (issue, then mail, like resend in phase 2). `resetPassword` and
     `changePassword` are `@Transactional`.
3. **`ProfileService`** (+ impl, `@Transactional(readOnly = true)` on the class):
   - `UserResponse get(Long userId)`: 404 via `ResourceNotFoundException("User", id)` when missing, as
     `currentUser` did.
   - `UserResponse update(Long userId, ProfileUpdateRequest)` (`@Transactional`): `user.updateProfile(...)`, then
     `saveAndFlush`, then the mapper.
4. **`ProfileController`** (`@RequestMapping("/api/auth/me")`):
   - `GET` → `profileService.get(Long.valueOf(jwt.getSubject()))`
   - `PUT` → `update(...)`
   - `PUT /password` → `@ResponseStatus(NO_CONTENT)`, with sid = `Long.valueOf(jwt.getClaimAsString(TokenService.SESSION_CLAIM))`
     (the validator guarantees it).

   Delete `GET /me` and `currentUser` from `AuthController` and `AuthService`.
5. **`AuthController`**: `POST /password/forgot` takes `EmailRequest` and answers 202. `POST /password/reset`
   answers 204. Both are public (`@SecurityRequirements`).
6. **`AuthControllerPasswordTest`**:
   - **Forgot:** a known email → 202 and a link recorded under `/reset-password?token=`. An unknown email → 202
     and no link. A second forgot kills the first link.
   - **Reset:** → 204. Afterwards the old password → 401 and the new one → 200. Access and refresh tokens from
     before the reset → 401.
   - **Bad links and passwords:** the same token again → 400. An expired token (moved to the past with
     `JdbcClient`) → 400. A weak new password → 400 naming `newPassword`.
   - **Side effects:** reset unlocks a locked account. Reset makes a never-verified account sign in (register, skip
     verify, forgot, reset, login → 200).
7. **`ProfileControllerTest`**:
   - **GET:** returns the profile with `emailVerified: true`.
   - **PUT:** `{" Jane ", " +84 90 123 4567 ", "vn", "usd"}` → `Jane` / `+84 90 123 4567` / `VN` / `USD`. A PUT
     without phone clears it. Country `XX` → 400 `countryCode`; currency `ABC` → 400 `preferredCurrency`; a
     blank name → 400.
   - **No token:** → 401.
   - **Change password:** a wrong current password → 400 and nothing is revoked. On success → 204: the caller's
     access token still gets 200 on `/me`, another session of the same user → 401, the new password signs in and
     the old one doesn't.
8. **Unit tests**:
   - `PasswordServiceImplTest`: a wrong current password never encodes and never revokes; a reset for a disabled
     user → 400.
   - `ProfileServiceImplTest`: missing user → 404; update goes through the entity and the mapper.

## Success Criteria

- [ ] The C-AUTH-04 and C-AUTH-06 acceptance criteria in `plan.md` are covered by passing tests.
- [ ] `GET /api/auth/me` keeps its path and every field it had; the new fields are additive.
- [ ] `./mvnw verify` is green.

## Risk Assessment

- **Forgot-password timing:** a known email does extra DB writes, so response time can reveal registered emails.
  This is minor and noted in README Known limitations (registration already reveals them with its 409).
- **Logged reset links** let anyone with log access take over accounts until an email service exists. A startup
  WARN covers it, and README says so.
- **Brute force through change password:** a stolen access token could try current passwords. Wrong attempts
  don't count toward lockout; this is left as an open question in `plan.md`.
- **"Pre-fill checkout" (C-BOOK-05):** this phase only exposes the data; checkout doesn't exist yet.
- **Rollback:** restore the scratchpad copy.
