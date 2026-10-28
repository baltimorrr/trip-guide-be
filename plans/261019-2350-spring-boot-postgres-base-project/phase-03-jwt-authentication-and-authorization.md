---
phase: 3
title: JWT authentication and authorization
status: completed
priority: P1
dependencies:
  - 2
effort: 3h
---

# Phase 3: JWT authentication and authorization

## Overview

Add stateless JWT security using Spring Security 7.1's built-in OAuth2 Resource Server and Nimbus encoder/decoder (HS256). It provides a users table with roles, register/login/me endpoints, admin bootstrap from env, role-based method security, and JSON ProblemDetail for 401/403.

Context: [phase 2](./phase-02-web-and-persistence-foundations.md), [research report](./reports/researcher-261019-2350-spring-boot-4-ecosystem-versions-report.md) (encoder/decoder builder API verified in the jar)

## Key Insights

- No jjwt and no custom JWT filter. `oauth2ResourceServer().jwt()` already does Bearer parsing, signature/exp/nbf validation and 401 on failure. We only issue tokens and map the `roles` claim to `ROLE_*` authorities.
- HS256 is enough for a single service that both issues and verifies tokens. Switch to RS256 (`NimbusJwtEncoder.withKeyPair`) only when other services must verify tokens without holding the signing secret.
- BCrypt only uses the first 72 **bytes**, and Spring Security rejects longer input. `@Size(max = 72)` counts chars, not bytes, so also check the UTF-8 byte length in the service.
- `DaoAuthenticationProvider` hashes a dummy password for unknown emails, which hides whether an email exists via response timing. Use it rather than calling `passwordEncoder.matches` by hand.
- Stateless tokens can't be revoked before they expire. That is an accepted limitation for this scope; it is documented, and the TTL is configurable.

## Requirements

- Functional:
  - `POST /api/auth/register` → 201 `UserResponse` with role USER. Duplicate email (case-insensitive) → 409
  - `POST /api/auth/login` → 200 `{accessToken, tokenType:"Bearer", expiresIn}`. Wrong password or unknown email → identical 401
  - `GET /api/auth/me` → 200 current user. Missing/invalid/expired token → 401 ProblemDetail + `WWW-Authenticate: Bearer`
  - Admin bootstrap: if `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` are set and no such user exists, create one with roles {ADMIN, USER} on startup
  - Method security: `@PreAuthorize("hasRole('ADMIN')")` works (used in phase 4); denial → 403 ProblemDetail
- Non-functional: the app refuses to start without a ≥32-byte base64 `APP_JWT_SECRET`; passwords are never logged or echoed

## Architecture

```text
user/      Role (enum USER, ADMIN) · User (entity) · UserRepository · AppUserDetailsService · AdminBootstrap
auth/      AuthController · AuthService · dto/{RegisterRequest, LoginRequest, TokenResponse, UserResponse}
security/  SecurityConfig · JwtConfig · JwtProperties · TokenService · ProblemDetailSecurityHandler
```

Request flows:

```text
login:  POST /api/auth/login ─► AuthService ─► AuthenticationManager(DaoAuthenticationProvider ─► AppUserDetailsService ─► DB)
                                   └─ success ─► TokenService.issue(user) ─► NimbusJwtEncoder(HS256) ─► TokenResponse
call:   GET /api/** + Bearer ─► BearerTokenAuthenticationFilter ─► NimbusJwtDecoder (sig, exp, iss)
                                   └─► JwtAuthenticationConverter (roles → ROLE_*) ─► controller / @PreAuthorize
errors: 401/403 in filters ─► ProblemDetailSecurityHandler ─► HandlerExceptionResolver ─► GlobalExceptionHandler (same JSON as MVC errors)
```

JWT claims: `iss` (=`app.jwt.issuer`), `sub` (=user id), `iat`, `exp`, `email`, `roles` (e.g. `["USER","ADMIN"]`).

Access rules (in `SecurityConfig`):

| Matcher | Rule |
|---------|------|
| `POST /api/auth/register`, `POST /api/auth/login` | permitAll |
| `EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)` | permitAll |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | permitAll (disabled in prod by phase 2) |
| `/error` | permitAll |
| anything else | authenticated |

## Related Code Files

- Modify: `spring-base/pom.xml`. Add `spring-boot-starter-security` and `spring-boot-starter-security-oauth2-resource-server`; test scope: `spring-boot-starter-security-test` and `spring-boot-starter-security-oauth2-resource-server-test`
- Modify: `Application.java` (add `@ConfigurationPropertiesScan`)
- Modify: `common/error/GlobalExceptionHandler.java` (add 401/403 handlers), `common/config/OpenApiConfig.java` (bearer scheme)
- Modify: `application.yml`, `src/test/resources/application-test.yml`, `.env.example`
- Create: `src/main/resources/db/migration/V1__create_users.sql`
- Create: `user/{Role,User,UserRepository,AppUserDetailsService,AdminBootstrap}.java`
- Create: `auth/{AuthController,AuthService}.java`, `auth/dto/{RegisterRequest,LoginRequest,TokenResponse,UserResponse}.java`
- Create: `security/{SecurityConfig,JwtConfig,JwtProperties,TokenService,ProblemDetailSecurityHandler}.java`
- Create tests: `security/TokenServiceTests.java`, `auth/AuthApiTests.java`, `user/UserRepositoryTests.java`

## Implementation Steps

1. **Migration** `V1__create_users.sql`:
   ```sql
   create table users (
       id            bigint generated by default as identity primary key,
       email         varchar(254) not null,
       password_hash varchar(100) not null,           -- "{bcrypt}" + 60-char hash
       full_name     varchar(100) not null,
       enabled       boolean      not null default true,
       version       bigint       not null default 0,
       created_at    timestamptz  not null,
       updated_at    timestamptz  not null,
       constraint uk_users_email unique (email)       -- app stores email lowercased
   );
   create table user_roles (
       user_id bigint      not null references users (id) on delete cascade,
       role    varchar(20) not null,
       primary key (user_id, role)
   );
   ```
2. **User entity**: extends `BaseEntity`. Lombok `@Getter @Setter @NoArgsConstructor(access = PROTECTED)`. No `@Data`, `@ToString` or `@EqualsAndHashCode`, because a generated `toString` would include `passwordHash` and lazy collections. Roles: `@ElementCollection(fetch = EAGER) @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id")) @Enumerated(STRING) @Column(name = "role") Set<Role> roles`. Add a static factory `User.create(email, passwordHash, fullName, Set<Role>)`.
3. **UserRepository**: `Optional<User> findByEmail(String email)`, `boolean existsByEmail(String email)`. Callers always pass a normalized email (`trim().toLowerCase(Locale.ROOT)`).
4. **JwtProperties**: `@Validated @ConfigurationProperties("app.jwt") record JwtProperties(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl)`.
5. **JwtConfig** (`@Configuration`):
   ```java
   @Bean SecretKey jwtSigningKey(JwtProperties p) {
       byte[] bytes = Base64.getDecoder().decode(p.secret());
       if (bytes.length < 32) throw new IllegalStateException(
           "app.jwt.secret must be base64 of >= 32 random bytes. Generate: openssl rand -base64 32");
       return new SecretKeySpec(bytes, "HmacSHA256");
   }
   @Bean JwtEncoder jwtEncoder(SecretKey key) {
       return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
   }
   @Bean JwtDecoder jwtDecoder(SecretKey key, JwtProperties p) {
       NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
       d.setJwtValidator(JwtValidators.createDefaultWithIssuer(p.issuer()));   // exp/nbf + iss
       return d;
   }
   @Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
       var authorities = new JwtGrantedAuthoritiesConverter();
       authorities.setAuthoritiesClaimName("roles");
       authorities.setAuthorityPrefix("ROLE_");
       var c = new JwtAuthenticationConverter();
       c.setJwtGrantedAuthoritiesConverter(authorities);
       return c;
   }
   ```
6. **TokenService.issue(User)** builds `JwtClaimsSet` (claims above, `exp = now + accessTokenTtl`) and returns `TokenResponse(token, "Bearer", ttl.toSeconds())`. Encode with `JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)`. The explicit HS256 header is required: the encoder otherwise defaults to RS256 and fails.
7. **AppUserDetailsService**: look up by the normalized email and map to `org.springframework.security.core.userdetails.User.withUsername(email).password(hash).roles(<role names>).disabled(!enabled).build()`. Use the fully qualified name to avoid a clash with our `User` entity. Throw `UsernameNotFoundException` when the user is missing.
8. **SecurityConfig** (`@Configuration @EnableMethodSecurity`):
   - `SecurityFilterChain`: CSRF disabled (no cookies; bearer header only), `cors(withDefaults())`, `STATELESS` sessions, `httpBasic`/`formLogin` disabled, matchers per the table above, `oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)).authenticationEntryPoint(handler))`, `exceptionHandling(e -> e.authenticationEntryPoint(handler).accessDeniedHandler(handler))`. The entry point must be set in **both** places: the resource-server one handles bad tokens, the other handles missing ones.
   - `PasswordEncoder`: `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt, `{id}` prefix allows future upgrades).
   - `AuthenticationManager`: `new ProviderManager(daoProvider)`, where `daoProvider` is a `DaoAuthenticationProvider` with `AppUserDetailsService` and the `PasswordEncoder`. Check the 7.x constructor at compile time; see the unverified list in the research report.
   - `CorsConfigurationSource` from `app.cors.allowed-origins` (comma list, default empty = no cross-origin). Allow methods GET/POST/PUT/DELETE and headers `Authorization`, `Content-Type`.
9. **ProblemDetailSecurityHandler** implements `AuthenticationEntryPoint` and `AccessDeniedHandler`. It injects `@Qualifier("handlerExceptionResolver") HandlerExceptionResolver`. On 401 it sets `WWW-Authenticate: Bearer`, then `resolver.resolveException(req, res, null, ex)`, so filter-level errors produce the same JSON as MVC errors.
10. **GlobalExceptionHandler** additions (MUST sit alongside the catch-all):
    - `AuthenticationException` → 401. Detail: `BadCredentialsException`/`AccountStatusException` → "Invalid email or password"; `OAuth2AuthenticationException` → "Invalid or expired access token"; otherwise "Authentication required"
    - `AccessDeniedException` → 403 "Access denied"
11. **DTOs** (records):
    - `RegisterRequest(@NotBlank @Email @Size(max=254) email, @NotBlank @Size(min=8, max=72) password, @NotBlank @Size(max=100) fullName)`
    - `LoginRequest(@NotBlank @Email email, @NotBlank password)`
    - **Override `toString()` in both request records to mask `password`.** Record `toString` prints every field, so any log line or debugger dump would leak it.
    - `TokenResponse(accessToken, tokenType, expiresIn)`
    - `UserResponse(id, email, fullName, roles, createdAt)` + `static from(User)`
12. **AuthService** (`@Service @RequiredArgsConstructor`):
    - `register`: normalize the email; if `password.getBytes(UTF_8).length > 72` → `BadRequestException("Password must be at most 72 bytes")` (phase 2, → 400). Then `existsByEmail` → `ConflictException("Email already registered")`; encode; save with `{USER}`. A concurrent duplicate falls through to `DataIntegrityViolationException` → 409 (phase 2 handler).
    - `login`: normalize; over 72 bytes → `BadCredentialsException`; `authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password))`; `findByEmail`; `tokenService.issue(user)`.
    - `currentUser(Jwt jwt)`: `findById(Long.valueOf(jwt.getSubject()))`, or throw `ResourceNotFoundException`.
13. **AuthController** `/api/auth`: `register` → `ResponseEntity.status(CREATED)`, `login`, and `me(@AuthenticationPrincipal Jwt jwt)`. Use `@Valid @RequestBody`.
14. **AdminBootstrap** (`ApplicationRunner`, `@Transactional`). Nested `@ConfigurationProperties("app.bootstrap.admin") record Properties(String email, String password)`. Skip (log INFO) if either value is blank. Skip if the user exists; never overwrite an existing password. Otherwise create it with {ADMIN, USER}, applying the same length/byte checks as register. Never log the password.
15. **OpenApiConfig**: add `@SecurityScheme(name = "bearerAuth", type = HTTP, scheme = "bearer", bearerFormat = "JWT")` and a global `@SecurityRequirement(name = "bearerAuth")`, so Swagger UI's Authorize button works.
16. **Config**:
    - `application.yml`:
      ```yaml
      app:
        jwt:
          secret: ${APP_JWT_SECRET}            # no default → startup fails if missing
          issuer: spring-base
          access-token-ttl: 1h
        bootstrap.admin:
          email: ${APP_BOOTSTRAP_ADMIN_EMAIL:}
          password: ${APP_BOOTSTRAP_ADMIN_PASSWORD:}
        cors.allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:}
      ```
    - `application-test.yml`: a literal test-only base64 32-byte secret (comment "TEST ONLY"), plus admin `admin@test.local` / `admin-test-password-123`
    - `.env.example`: add `APP_JWT_SECRET=` (comment: `openssl rand -base64 32`), `APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com`, `APP_BOOTSTRAP_ADMIN_PASSWORD=`, `APP_CORS_ALLOWED_ORIGINS=`
17. **Tests**:
    - `TokenServiceTests` (plain JUnit; build beans by calling `JwtConfig` methods directly). Cover: an issued token decodes with the correct `sub`/`roles`/`iss` and `exp - iat == ttl`; a token signed with a different key → `JwtException`; a manually encoded token with `exp` 5 minutes in the past → `JwtValidationException`; wrong `iss` → rejected.
    - `AuthApiTests` (`@IntegrationTest`, `MockMvcTester`). Use a unique email per test (`UUID`) so tests never collide on the shared DB. Cover:
      - register 201 (no `password`/`passwordHash` in the body)
      - duplicate → 409; `UPPER@x.com` after `upper@x.com` → 409
      - blank/short password → 400 with `errors[]`; a 73-byte multibyte password → 400
      - login 200; wrong password → 401 and unknown email → 401, with identical bodies
      - `/me`: no token → 401 + `WWW-Authenticate`; garbage token → 401; valid → 200
      - bootstrap admin login → token decodes with `ADMIN`
    - `UserRepositoryTests` (`@DataJpaTest`, `@Import(TestcontainersConfiguration.class)`, `@AutoConfigureTestDatabase(replace = NONE)`). Cover: save + `findByEmail`; roles persisted to `user_roles`; duplicate email → `DataIntegrityViolationException` on `saveAndFlush`.
    - Check that `ApplicationTests` and `ErrorResponseTests` still pass: `/actuator/health` and `/v3/api-docs` are permitAll. **Update the unknown-path test:** without a token it now returns 401 problem+json (authenticated rule); with a valid token it still returns 404 problem+json.

## Success Criteria

- [x] `./mvnw verify` green
- [x] Without `APP_JWT_SECRET`, the app fails at startup with a clear message; with a 16-byte secret, it fails with the "≥ 32 bytes" message
- [x] Manual curl: register → login → `GET /api/auth/me` with the token → 200; the same call with one character of the token changed → 401 JSON
- [ ] Swagger UI → Authorize with the token → `/api/auth/me` "Try it out" → 200 — *spec verified (bearerAuth scheme, public login/register); manual UI click-through not done*
- [x] `./mvnw verify 2>&1 | grep -c 'admin-test-password'` → `0` (no password values in logs)

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| `DaoAuthenticationProvider` API changed in Security 7 | compile error surfaces it immediately; follow the 7.1 Javadoc |
| Catch-all handler turns 401/403 into 500 | explicit handlers (step 10) + tests in this phase and phase 4 |
| Entry point set in only one place → bad-token 401 has an empty body | configure both (step 8); the garbage-token test asserts a JSON body |
| Token theft / no revocation | short configurable TTL; the README documents this; refresh/revocation deferred (see plan "Not In Scope") |
| Login brute force | deferred (rate limiting not in scope); documented in README |
| Registration reveals existing emails via 409 | accepted trade-off for a template; documented |
| Disabled user keeps a valid token until expiry | `/me` reloads the user from the DB; the README documents that the TTL bounds exposure |

## Security Considerations

- Secret: ≥256-bit, from env only, never committed; the test secret exists only in `src/test/resources`
- Passwords: bcrypt via delegating encoder; 72-byte guard; masked `toString`; never returned in responses
- Errors: login failures give a uniform 401; no stack traces; `WWW-Authenticate: Bearer` on 401
- CSRF disabled deliberately (no cookie auth). If cookie-based auth is ever added, CSRF must be re-enabled.
