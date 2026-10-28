---
phase: 3
title: Feature layering
status: completed
priority: P2
dependencies:
  - 2
---

# Phase 3: Feature layering

## Overview

Split `auth/`, `user/`, `product/` into layer subfolders, turn feature services into interface + `impl/`, move DTO
conversion into hand-written mappers, and move tests to mirror packages. Behavior is unchanged.

## Requirements

- Functional: same endpoints, responses, transactions, and security rules.
- Non-functional: controllers and other services depend on service **interfaces**; no new dependency; no empty dirs.

## Architecture

- **Services**: interface `x/services/XService` (public contract + Javadoc on what it does);
  `x/services/impl/XServiceImpl` is `@Service @RequiredArgsConstructor` with the existing body and the existing
  `@Transactional` annotations (transaction rules belong to the implementation). Constructor injection keeps
  impls unit-testable without Spring.
- **`UserService` statics**: `MAX_PASSWORD_BYTES` (with its BCrypt Javadoc) and
  `static boolean exceedsPasswordByteLimit(String)` move onto the interface. `AuthServiceImpl` calls them as today.
- **Mappers**: `@Component` classes, injected like any bean. They replace the `static from(...)` methods on DTOs.
  - `UserMapper.toResponse(User)`: roles sorted, the same as today's `UserResponse.from`.
  - `ProductMapper.toEntity(ProductRequest)` → `Product.create(...)`; `updateEntity(Product, ProductRequest)` →
    `product.update(...)`; `toResponse(Product)`. Entities keep their factory/update methods (trim logic stays there).
- **Not interface+impl**: `TokenService` and `AppUserDetailsService` are security infrastructure, not feature
  services. They stay concrete in `auth/security/` (`AppUserDetailsService` already implements Spring's
  `UserDetailsService`). `JwtConfig`, `JwtProperties`, `TokenService` share a package because `JwtConfig` and
  `TokenServiceTest` use package-private members (`ROLES_CLAIM`, `EMAIL_CLAIM`, `@Bean` methods).
- **Dependency direction**: `auth → user`, `product → common`, `user → common`; `common → nothing`.

## Related Code Files

Main, under `spring-base/src/main/java/com/company/project/`:

| From | To |
|------|----|
| `auth/AuthController.java` | `auth/controller/AuthController.java` |
| `auth/AuthService.java` | `auth/services/AuthService.java` (interface) + `auth/services/impl/AuthServiceImpl.java` |
| `auth/dto/LoginRequest.java`, `RegisterRequest.java`, `TokenResponse.java` | unchanged location |
| `auth/dto/UserResponse.java` | `user/dto/UserResponse.java` (drop `from`) |
| `security/JwtConfig.java`, `JwtProperties.java`, `TokenService.java` | `auth/security/` |
| `user/AppUserDetailsService.java` | `auth/security/AppUserDetailsService.java` |
| `user/User.java`, `user/Role.java` | `user/model/` |
| `user/UserRepository.java` | `user/repository/` |
| `user/UserService.java` | `user/services/UserService.java` (interface) + `user/services/impl/UserServiceImpl.java` |
| `user/AdminBootstrap.java`, `AdminBootstrapProperties.java` | `user/bootstrap/` |
| — | `user/mapper/UserMapper.java` (create) |
| `product/ProductController.java` | `product/controller/` |
| `product/Product.java` | `product/model/` |
| `product/ProductRepository.java` | `product/repository/` |
| `product/ProductService.java` | `product/services/ProductService.java` (interface) + `product/services/impl/ProductServiceImpl.java` |
| `product/dto/ProductRequest.java`, `ProductResponse.java` | unchanged location (`ProductResponse` drops `from`) |
| — | `product/mapper/ProductMapper.java` (create) |

After this, the `security/` package is gone.

Tests, under `spring-base/src/test/java/com/company/project/`:

| From | To |
|------|----|
| `auth/AuthApiTests.java` | `auth/controller/AuthControllerTest.java` |
| `security/TokenServiceTests.java` | `auth/security/TokenServiceTest.java` |
| `user/UserRepositoryTests.java` | `user/repository/UserRepositoryTest.java` |
| `user/AdminBootstrapTest.java` | `user/bootstrap/AdminBootstrapTest.java` |
| `product/ProductApiTests.java` | `product/controller/ProductControllerTest.java` |
| — | `user/services/impl/UserServiceImplTest.java` (create) |
| — | `product/services/impl/ProductServiceImplTest.java` (create) |
| `ProjectApplicationTest`, `TestProjectApplication`, `TestcontainersConfiguration`, `support/*` | unchanged |

## Implementation Steps

Do one feature at a time and compile after each (`./mvnw -q test-compile`):

1. **user**: `git mv` model/repository/bootstrap files; split `UserService` into interface + `UserServiceImpl`;
   move `UserResponse` to `user/dto`; create `UserMapper`. Fix imports in `auth/` and `security/`.
2. **auth**: `git mv` controller; split `AuthService` into interface + `AuthServiceImpl` (inject `UserMapper`, use
   `userMapper::toResponse`); move the JWT trio and `AppUserDetailsService` to `auth/security/`.
3. **product**: `git mv` controller/model/repository; split `ProductService` into interface + `ProductServiceImpl`
   (inject `ProductMapper`); create `ProductMapper`; drop `ProductResponse.from`.
4. Move/rename the test classes per the table (class names and packages only; leave assertions untouched).
5. Add `UserServiceImplTest` (Mockito `@ExtendWith(MockitoExtension.class)`, mocks `UserRepository` + `PasswordEncoder`):
   - password over 72 bytes (e.g. `"é".repeat(37)`) → `BusinessException` `BAD_REQUEST`, no repository/encoder calls
   - existing email (looked up normalized: `" Jane@Example.com "` → `"jane@example.com"`) → `CONFLICT`
   - success saves normalized email, trimmed full name, encoded hash, the given roles
6. Add `ProductServiceImplTest` (mock `ProductRepository`, real `ProductMapper`):
   - `findById` missing → `ResourceNotFoundException`
   - `create` saves trimmed name via `saveAndFlush` and returns the mapped response
   - `update` applies the request to the loaded product
   - `delete` missing → not found and `delete` is never called
7. `find src -type d -empty -delete`, then `./mvnw clean verify`.

## Success Criteria

- [ ] `./mvnw clean verify` green; every moved test passes with unchanged assertions.
- [ ] Controllers import only `services.XService` interfaces, never `impl` (`grep -rn "services.impl" src/main/java --include='*Controller.java'` empty).
- [ ] No DTO has a `static from(` method (`grep -rn "static .* from(" src/main/java/com/company/project/*/dto` empty).
- [ ] `find src -type d -empty` prints nothing; `security/` no longer exists.

## Risk Assessment

- **Package-private breakage**: `AdminBootstrap` (package-private class), `JwtConfig` `@Bean` methods, and the
  `TokenService` claim constants need their tests in the same package. The mapping above keeps each pair together.
  The compiler catches any miss.
- **Bean ambiguity**: exactly one implementation per interface, so injection by interface type is unambiguous.
  Boot's default CGLIB proxies keep `@Transactional` working the same.
- **Mockito on JDK 21** prints a self-attach agent warning. It is a warning only; leave surefire config alone unless it fails.
- **Accidental behavior change while splitting**: copy bodies verbatim and diff each impl against its old class
  (`git diff -M` shows renames with small deltas).
- Rollback: `git reset --hard HEAD && git clean -fd src` (HEAD = phase 2 commit).
