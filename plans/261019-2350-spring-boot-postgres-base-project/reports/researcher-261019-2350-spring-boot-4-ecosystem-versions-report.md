# Research: Spring Boot 4.1 ecosystem for a PostgreSQL + JWT base project

Date: 2026-10-19. All facts below verified today against live sources (not memory) unless marked.

## Versions (verified)

| Item | Version | Source |
|------|---------|--------|
| Spring Boot (stable default) | 4.1.1 | start.spring.io metadata (`bootVersion.default`) |
| Spring Framework | 7.0.9 | spring-boot-dependencies-4.1.1.pom |
| Spring Security | 7.1.1 | same BOM |
| Hibernate ORM | 7.4.5.Final | same BOM |
| Flyway | 12.4.0 | same BOM |
| Jackson | 3.1.5 (package `tools.jackson.*`) | same BOM |
| JUnit Jupiter | 6.0.3 | same BOM |
| Lombok | 1.18.46 | same BOM |
| PostgreSQL JDBC | 42.7.13 | same BOM |
| Testcontainers | 2.0.5 | same BOM + Maven Central |
| springdoc-openapi-starter-webmvc-ui | 3.1.1 (NOT Boot-managed; pin explicitly) | Maven Central metadata; 3.1.0 release notes say "Upgrade Spring Boot to 4.1.0" |
| Maven wrapper | 3.3.4, Maven 3.10.0 | Initializr-generated `.mvn/wrapper/maven-wrapper.properties` |
| Local JDK | OpenJDK 21.0.12 | `java -version` |
| Docker / Compose | 28.1.1 / v2.35.1, daemon reachable | `docker ps` |

## Boot 4 starter names (verified from Initializr-generated pom.xml)

Boot 4 modularized starters. Old Boot 3 tutorials use different names.

- `spring-boot-starter-webmvc` (was `-web`)
- `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`
- `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` (Flyway now needs its own starter)
- `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`
- `spring-boot-docker-compose` (runtime, optional), `org.postgresql:postgresql` (runtime)
- Test starters are per-module: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-flyway-test`, `-security-test`, `-security-oauth2-resource-server-test`, `-validation-test`, `-actuator-test`
- Testcontainers 2: `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql`; class `org.testcontainers.postgresql.PostgreSQLContainer` (package moved in TC 2)
- Initializr wires Lombok + `spring-boot-configuration-processor` into `maven-compiler-plugin` `annotationProcessorPaths`

## Boot 4 test annotation packages (verified from jars)

- `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
- `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
- `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`
- `org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest`

## JWT approach (verified from spring-security-oauth2-jose-7.1.1.jar)

Use Spring Security's built-in Nimbus support; no jjwt/third-party lib needed.

- `NimbusJwtEncoder.withSecretKey(SecretKey).algorithm(MacAlgorithm.HS256).build()`; builder class `NimbusJwtEncoder$SecretKeyJwtEncoderBuilder` exists
- `NimbusJwtDecoder.withSecretKey(SecretKey).macAlgorithm(MacAlgorithm.HS256).build()`; supports `validateType`, `jwtProcessorCustomizer`
- Resource server: `http.oauth2ResourceServer(o -> o.jwt(...))`; a custom `JwtDecoder` bean makes the auto-config back off
- HS256 needs a secret of at least 256 bits (32 bytes). Source: https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-hmac

## Generated reference test config (Initializr, Boot 4.1.1)

```java
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
  @Bean @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
  }
}
```
Plan pins `postgres:18-alpine` instead of `latest` for reproducibility.

## Unverified (check during implementation; compile/test will prove)

- `DaoAuthenticationProvider` constructor signature in Security 7.x (believed `new DaoAuthenticationProvider(userDetailsService)`)
- Whether BCrypt `matches()` throws or returns false for passwords > 72 bytes in 7.1 (plan guards before calling)
- `spring.config.import: optional:file:.env[.properties]` resolves relative to the working dir (documented Boot behavior; README must say "run from project root")
- Boot 4 packages for `AutoConfigureTestDatabase` and `HealthEndpoint`/`InfoEndpoint` (moved with modularization; IDE auto-import resolves)
- postgres:18 image data mount at `/var/lib/postgresql` (PG18 image layout change; verify data survives container restart)

## Sources

- https://start.spring.io/metadata/client
- https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/
- https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml
- https://newreleases.io/project/github/springdoc/springdoc-openapi/release/v3.1.0
- https://docs.spring.io/spring-security/reference/7.1/servlet/oauth2/index.html
- https://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-hmac
