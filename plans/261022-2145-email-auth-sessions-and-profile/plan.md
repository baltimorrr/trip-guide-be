---
title: >-
  Email auth: sign-up verification, sessions, lockout, password reset, profile
  (C-AUTH-01/02/04/05/06)
description: >-
  Turn spring-base's register/login into the hotel platform's email account
  flow: verified sign-up (link logged, no email service yet), sign-in with
  lockout, 15-minute access tokens plus rotating per-device refresh sessions
  with reuse detection and immediate revocation, sign out (this device / all
  devices), forgot/reset/change password, and profile editing.
status: completed
priority: P2
tags:
  - backend
  - api
  - database
  - auth
  - security
blockedBy: []
blocks: []
created: 2026-10-22T00:00:00.000Z
---

# Email auth: sign-up verification, sessions, lockout, password reset, profile (C-AUTH-01/02/04/05/06)

## Overview

Sources: `spring-base/USER_STORIES.md` §1 and §10 (security NFRs, open session question),
`spring-base/DATABASE-SCHEMA.md` (`users`, `user_tokens`, `user_sessions`, `token_type`).
Builds on the completed plans in `plans/` (base project, layered restructure, homepage search). Today's code
has register (no verification), login → 1h JWT, `GET /api/auth/me`; no refresh tokens, revocation, lockout,
reset or profile edit. All unfinished-plan scan: none open, no cross-plan dependency.

## User Decisions (2026-10-22, do not reverse silently)

| Decision | Choice |
|----------|--------|
| Email delivery | **None yet.** Verification and reset links are written to the server log behind an `AuthMailer` interface |
| Refresh sessions | **Rotate on every refresh + reuse detection.** One `user_sessions` row per sign-in (device). Adds `previous_token_hash` and `last_used_at`. Presenting the previous token again revokes that session |
| Refresh token transport | **JSON body.** Returned by login/refresh, sent in the body of refresh/logout. No cookies, CSRF stays off |
| Access-token revocation | **Immediate.** Access token carries `sid`; every authenticated request checks the session is not revoked/expired and the user is enabled (one indexed lookup) |
| Scope | **Hold.** The 5 stories + resend-verification endpoint + defaults below. Emails only logged |
| Git | No commits or staging by Claude; rollback point is a scratchpad copy |
| Verification | Inline (`./mvnw verify`, curl smoke on ports 8081/5434); review agents offered, not auto-run |

## Planner Defaults (changeable)

- Roles stay `USER`/`ADMIN` (role-model change remains open in CLAUDE.md; these stories don't need it).
- Access token TTL `1h` → `15m`. Refresh session lifetime is absolute from sign-in (rotation does not extend it):
  `1d`, or `30d` with "remember me".
- Opaque tokens (refresh, verify, reset): 32 random bytes, base64url; only the SHA-256 hex is stored.
- Unverified account + correct password → **403 "Email not verified"**. Wrong password → generic 401 as today.
- Lockout: 5 failures within 15 min (window from first failure) → locked 15 min. Locked → same generic 401,
  password not checked. Failures counted by one atomic JPQL `update versioned` (no lost updates).
- Forgot password / resend verification always answer **202** (no account enumeration); a new link invalidates
  older unused links of that type; per-account cooldown 1 min between links.
- Reset password: link 30 min, single use, sets password, marks email verified, clears lockout, revokes all sessions.
  Change password: needs current password, revokes all sessions except the current one (`sid`).
- Verification link 24h, single use. Links point to the frontend: `${APP_FRONTEND_URL}/verify-email?token=…` and
  `/reset-password?token=…`; the frontend POSTs the token to the API.
- Existing users are marked verified by the migration; the bootstrap admin is created verified.
- Profile: `fullName`, `phone`, `countryCode` (ISO 3166 alpha-2, nationality), `preferredCurrency` (ISO 4217,
  checked with `java.util.Currency`). `preferred_currency` gets its FK when `currencies` is created (existing
  convention). `ip` stored as `varchar(45)` instead of the doc's `inet` (no mapping friction).
- Public `/api/auth/*` POST endpoints ignore any `Authorization` header, so a client holding an expired access
  token can still refresh/log in/log out (today any bad token on a public endpoint is a 401).
- Profile endpoints stay under `/api/auth/me` (existing contract), in a new `ProfileController`.

## Phases

| Phase | Name | Status | Depends on |
|-------|------|--------|------------|
| 1 | [Schema and auth building blocks](./phase-01-schema-and-auth-building-blocks.md) | Completed | — |
| 2 | [Sign-up and email verification](./phase-02-sign-up-and-email-verification.md) | Completed | 1 |
| 3 | [Sign-in sessions lockout and sign-out](./phase-03-sign-in-sessions-lockout-and-sign-out.md) | Completed | 2 |
| 4 | [Password reset change password and profile](./phase-04-password-reset-change-password-and-profile.md) | Completed | 3 |
| 5 | [Docs and final verification](./phase-05-docs-and-final-verification.md) | Completed | 4 |

Strictly sequential: every phase edits `AuthController`/`SecurityConfig`/`TestAuth`.

## API After This Plan

| Method | Path | Access | Result | Story |
|--------|------|--------|--------|-------|
| POST | `/api/auth/register` | public | 201 `UserResponse`; logs verify link | 01 |
| POST | `/api/auth/verify-email` | public | 204; 400 invalid/expired/used link | 01 |
| POST | `/api/auth/verify-email/resend` | public | 202 always | 01 |
| POST | `/api/auth/login` | public | 200 `{accessToken, tokenType, expiresIn, refreshToken, refreshExpiresIn}`; 401 generic; 403 unverified | 02 |
| POST | `/api/auth/refresh` | public | 200 new token pair; 401 invalid/expired/reused | 02 |
| POST | `/api/auth/logout` | public | 204 always (revokes the session of that refresh token) | 05 |
| POST | `/api/auth/logout-all` | authenticated | 204, every session of the user revoked | 05 |
| POST | `/api/auth/password/forgot` | public | 202 always; logs reset link | 04 |
| POST | `/api/auth/password/reset` | public | 204; 400 invalid/expired/used link | 04 |
| GET | `/api/auth/me` | authenticated | profile (adds `phone`, `countryCode`, `preferredCurrency`, `emailVerified`) | 06 |
| PUT | `/api/auth/me` | authenticated | 200 updated profile | 06 |
| PUT | `/api/auth/me/password` | authenticated | 204; 400 wrong current password | 06 |

## Acceptance Criteria

- [x] C-AUTH-01: register needs fullName, email, password, confirmPassword (phone optional); weak password
      (< 8 chars or no letter/no digit) and mismatch → 400 field errors; duplicate email → 409 "Email already registered".
- [x] C-AUTH-01: register logs a verify link; until it is used, login with the right password → 403; after
      `POST /verify-email` login works; a used/expired/unknown token → 400; resend issues a fresh link (old one dies).
- [x] C-AUTH-02: wrong password and unknown email → identical 401; 5 failures in 15 min lock the account (even the
      right password → 401) until `locked_until`; a success resets the counter.
- [x] C-AUTH-02: login returns a 15-min access token and a refresh token; only its SHA-256 is stored, one row per
      sign-in with user agent and IP; `rememberMe: true` → 30-day session, otherwise 1 day.
- [x] Refresh rotates the token; the old token then fails, and replaying it revokes the session (the newest token
      and its access tokens stop working immediately).
- [x] C-AUTH-05: logout revokes only that device's session (its access token → 401 at once, other devices keep
      working); logout-all revokes every session.
- [x] C-AUTH-04: forgot-password → 202 for known and unknown emails; reset link works once, expires after 30 min;
      reset sets the password and revokes every session.
- [x] C-AUTH-06: GET/PUT `/api/auth/me` reads/updates name, phone, nationality, currency (invalid ISO codes → 400);
      change password requires the current one and revokes other sessions but not the current one.
- [x] No raw token or password in responses other than the token response, in `toString()` of DTOs, or in the DB.
- [x] `./mvnw verify` passes; local curl smoke covers sign-up → log link → verify → login → refresh → logout.

## Out of Scope

Real email sending / `email_outbox` (C-PAY-03 etc.), Google sign-in (C-AUTH-03), delete account (C-AUTH-07),
per-IP sign-in rate limiting (stays in README Known limitations), listing/naming sessions, scheduled cleanup of
expired sessions/tokens, role-model change, email change, any frontend.

## Risks (summary; details in phases)

- Every existing integration test logs in via `TestAuth`; unverified login now fails → `TestAuth` must verify
  through a recording mailer (phase 1/2) or the whole suite breaks.
- Reuse revocation must commit even though refresh then throws 401 (`noRollbackFor`), or detection is a no-op.
- Concurrent refreshes from two tabs: the second is treated as reuse and signs that device out. Documented;
  clients must send one refresh at a time.
- Links (= account takeover for reset) appear in logs, including prod. Startup WARN + README limitation.

## Open Questions

None blocking. Worth a product check later: 403 (vs generic 401) for unverified accounts with the right
password; 1-day non-remember-me session; whether change-password failures should count toward lockout.

## Implementation Notes (2026-10-23)

- Result: 165 tests green (`./mvnw verify`), 27/27 live curl checks pass against a throwaway Testcontainers database
  (`./mvnw spring-boot:test-run -Dspring-boot.run.arguments=--spring.docker.compose.enabled=false`). Nothing committed;
  rollback copy in the session scratchpad.
- Moved earlier than planned: `UserService.create(…, phone, roles, emailVerified)` and `changePassword` landed in phase 1
  so the build compiled after the entity change.
- Corrected during phase 3: `DelegatingOAuth2TokenValidator` runs every validator, so `JwtConfig` chains the session
  check by hand after the standard ones (expired tokens never hit the database; covered by `TokenServiceTest`).
- Verified the key invariant by mutation: without `noRollbackFor` on `SessionServiceImpl.refresh`,
  `AuthControllerSessionTest#replayedRefreshTokenRevokesTheSession` fails.
- `User` lost Lombok `@Setter` (nothing used it); state changes go through domain methods like `Product`.
- Found, not caused here: the local Compose database (`hotel-booking-system`, port 5434) fails Flyway validation on V3,
  which was edited on 2026-10-22 after it ran locally. Left untouched for the user to reset.
