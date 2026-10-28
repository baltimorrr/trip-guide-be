---
phase: 5
title: Docs and final verification
status: completed
priority: P2
effort: 1h
dependencies:
  - 4
---

# Phase 5: Docs and final verification

## Overview

Bring README, CLAUDE.md, USER_STORIES.md and DATABASE-SCHEMA.md in line with the new auth behavior, then verify the
whole suite and run a live curl smoke test against the local app with the links taken from the server log.

## Context

- There is no `docs/` folder at the repo root or in `spring-base/`. The project docs are the four files in
  `spring-base/`; read each one before editing it.
- In USER_STORIES.md: the status legend is at line 36 (✅ = every acceptance criterion is met by the API,
  🟡 = what's left is listed under the story), and the open sessions question is in §10.
- `.env` (gitignored) on this machine uses `SERVER_PORT=8081` and `POSTGRES_PORT=5434`. Ports 8080 and 5432 are
  taken by the user's other services. Don't touch those.

## Requirements

- Every claim in the docs matches the code: endpoints, status codes, settings, defaults, and the known limits.
- Nothing is committed: report the changes as uncommitted.

## Related Code Files

- Modify: `spring-base/README.md`, `spring-base/CLAUDE.md`, `spring-base/USER_STORIES.md`, `spring-base/DATABASE-SCHEMA.md`
- (Already modified in phase 1: `spring-base/.env.example`)

## Implementation Steps

1. **README.md**:
   - **Intro:** mention the account stories.
   - **Endpoints table:** use the API table from `plan.md`.
   - **"Try it with curl":** register (with `confirmPassword`) → copy the link from the app log (`grep "verification
     link"`) → `POST /verify-email` → login (the refresh token is in the JSON) → `/me` → `/refresh` → `/logout`.
   - **New "Accounts and sessions" section:**
     - Token pair and lifetimes: 15m access token; refresh token 1d, or 30d with remember me.
     - Refresh rotation and the reuse rule. Clients must send one refresh at a time.
     - Immediate revocation through `sid`.
     - Lockout rule; the 403 for unverified accounts.
     - The link format (`APP_FRONTEND_URL`), and how a frontend page posts the token.
   - **Configuration table:** `APP_FRONTEND_URL`. The `app.auth.*` settings are described in prose, like
     `app.search.*`. `access-token-ttl` is now 15m.
   - **Tests table:** the new classes from phases 1–4, plus `RecordingAuthMailer` and `TestAuth.registerVerified`.
   - **Project structure:** `auth/` gains `config/ mail/ model/ repository/ (+ impl/)`; `common/validation/`;
     `V4` in the migration list.
   - **Known limitations:**
     - Replace "No refresh tokens or revocation" with:
       - Emails are not sent; links are logged at INFO in every profile (anyone with log access can reset
         passwords).
       - Concurrent refreshes of one token sign that device out.
     - Keep "No rate limiting on login", reworded: there is per-account lockout but no per-IP limit, and lockout
       can be used to block a known email for 15 minutes.
     - Keep "Registration reveals whether an email exists". Add that forgot/resend don't, apart from timing.
   - **Production notes:** set `APP_FRONTEND_URL`; behind a proxy, set `server.forward-headers-strategy` so session
     IPs are the client's.
2. **CLAUDE.md**:
   - Rewrite the **Authentication** paragraph:
     - Session rows and token hashing.
     - `SessionTokenValidator` in the decoder (one DB lookup per authenticated request).
     - The public auth endpoints ignore `Authorization` (the shared `PUBLIC_AUTH_ENDPOINTS` matcher).
     - Lockout is atomic JPQL in `UserRepositoryImpl`.
     - `refresh` uses `noRollbackFor`, and why.
   - Mark the **Decided (2026-10-22)** session design: rotate + reuse detection, JSON body, immediate revocation.
     Keep roles as "still open".
   - **Tests section:** `RecordingAuthMailer` (`@Primary`, imported by `@IntegrationTest`; read links through its
     static helpers); `TestAuth.userToken` registers *and verifies*.
   - **Conventions:** update the known-limitations line (refresh tokens now exist).
3. **USER_STORIES.md**:
   - C-AUTH-02 and C-AUTH-05: **✅ Implemented**.
   - C-AUTH-01 and C-AUTH-04: **🟡 Partly implemented**, with "Left: send the email (the link is only logged)".
   - C-AUTH-06: **🟡 Partly implemented**, with "Left: pre-filling checkout comes with C-BOOK-05".
   - §10: move the session question to **Decided**: rotating refresh token with reuse detection, last used time,
     no device name; access tokens checked against their session on every request.
4. **DATABASE-SCHEMA.md**:
   - `users`: add `first_failed_login_at`.
   - `user_sessions`: add `previous_token_hash` and `last_used_at`, plus a short note that `ip` is stored as
     varchar in the code.
   - Add a note at the top saying these tables were edited by hand to match the code, like the `destinations` note.
     Keep the doc's types (uuid, citext…) elsewhere; the CLAUDE.md conventions translate them.
5. **Verify**:
   - `cd spring-base && ./mvnw verify` → all tests green. Report the counts and any failures as they are.
   - Static checks:
     - `grep -rn "@Query" src/main` → none on repository interfaces.
     - `grep -rn "JdbcClient" src/main` → none.
     - No plan/phase IDs in code comments or migration names.
     - Every new request DTO that carries a secret overrides `toString()`.
6. **Live smoke** (local profile, `.env` ports; the app runs in the background with its log in the scratchpad):
   - **Sign-up:** register → the log has a verification link → login → 403 → verify → login → 200.
   - **Refresh:** refresh → 200. Replay the old refresh token → 401, then `/me` with the newest access token → 401.
   - **Sign-out:** login A and B → `logout` A → A's `/me` 401, B's `/me` 200. `logout-all` → B 401.
   - **Password reset:** forgot → the log has a reset link → reset → old password 401, new password 200.
   - **Lockout:** 5 wrong passwords → the right password 401.
   - **Storage:** `psql` on 5434 → `select length(refresh_token_hash) from user_sessions` returns 64, and no raw
     token appears in `user_tokens`.
   - Stop the app. Leave the Compose database running (the project's default); never stop other containers.
7. Mark the plan's phases complete with `ck plan check`. The final report lists the changed files, the test
   results, the smoke results, and that nothing is committed.

## Success Criteria

- [ ] `./mvnw verify` passes.
- [ ] The smoke steps behave as listed, using links copied from the log.
- [ ] The docs' endpoint and limitation lists match the code; story statuses are honest (🟡 where email or checkout
      is still missing).

## Risk Assessment

- **Docs drift:** re-read each changed section against the controllers and `application.yml` before finishing.
- **Smoke run port clash:** use the `.env` ports. Find the process with a bracketed `pgrep -f "[c]om.company.project"`
  so the shell's own command line doesn't match.
