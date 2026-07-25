# Coupon Service

REST API for creating and redeeming discount coupons with country-based restrictions. Built with Spring Boot 4.1 and Java 25.

## Features

- Create coupons with a unique code, usage limit and country restriction (ISO 3166-1 alpha-2)
- Redeem coupons with per-user uniqueness enforcement and IP-based geolocation verification
- Optimistic locking for safe concurrent access
- Circuit breaker + retry on the geolocation provider (Resilience4j)
- Liquibase database migrations
- OpenAPI / Swagger UI documentation
- Request-level correlation IDs propagated through MDC and echoed on responses
- Field-level validation errors surfaced through RFC 7807 ProblemDetail
- ArchUnit tests enforcing hexagonal layer boundaries
- JaCoCo code coverage enforcement (80% minimum)
- CI/CD pipeline: GitHub Actions → GHCR → OpenShift
- Kubernetes manifests with security hardening and NetworkPolicy

## Architecture

The project follows Hexagonal Architecture (Ports & Adapters):

```
domain/          Pure Java domain model, no framework dependencies
application/     Use case ports (input/output) and application service
infrastructure/  Spring-managed adapters: JPA, geolocation REST client,
                 transaction boundaries, resilience decorator, configuration
presentation/    REST controllers, DTOs, global exception handler
```

All dependencies point inward. Domain knows nothing about Spring, JPA or HTTP. Application defines port interfaces but has no idea who implements them. Infrastructure and presentation depend on the inner layers, never the other way around.

There are two kinds of ports. Input ports (`CreateCouponUseCase`, `UseCouponUseCase`) define what the application can do. The controller calls them without knowing the implementation. Output ports (`CouponRepository`, `CouponUsageRepository`, `GeoLocationProvider`) define what the application needs from the outside world. Infrastructure provides the adapters: `JpaCouponRepositoryAdapter` implements `CouponRepository`, `GeoLocationAdapter` implements `GeoLocationProvider`, and so on.

One thing worth noting is that `CouponUseCaseImpl` lives in the infrastructure package, not in application. That is intentional. It depends on Spring (`@Component`, `@Slf4j`) and on `CouponTransactionBoundary` which is a Spring-managed transactional wrapper. The application layer stays clean, containing only the port interfaces, command records and `CouponService` (which is framework-free, instantiated manually via `@Bean`).

The adapter layer introduces an extra mapping step between domain and persistence. `CouponMapper` converts between `Coupon` (domain) and `CouponEntity` (JPA). This means Hibernate annotations never leak into the domain model, and changes to the DB schema do not force changes in domain classes. The JPA repositories (`JpaCouponRepository`) are standard Spring Data interfaces, but they are hidden behind the adapter and never referenced outside of infrastructure.

## Design Decisions

### Framework-free domain and application layers

`CouponService` is a plain Java class with no `@Service`, no `@Component`, no Spring imports. It gets instantiated via an explicit `@Bean` method in `BeanConfiguration`. The entire domain and application layer can be unit-tested without a Spring context and could be reused in a non-Spring runtime.

Same goes for the domain model: `Coupon`, `CouponCode`, `Country` and all domain exceptions are pure Java with zero framework dependencies.

### Rich domain model with self-validating Value Objects

`CouponCode` and `Country` are Java records with validation in their compact constructors. You simply cannot create an instance of `Country` with an invalid ISO code or a blank `CouponCode`. Invariants are enforced at construction time, not scattered across service methods.

`Coupon` uses factory methods to separate creation concerns. `Coupon.create()` generates a new ID, sets the timestamp and validates `maxUsages > 0`. `Coupon.reconstitute()` rebuilds from persisted state, trusting the data. This way persistence adapters cannot accidentally bypass domain rules.

Equality is based solely on the aggregate ID (`UUID`), following the DDD entity identity pattern.

### Transaction Boundary pattern

`CouponTransactionBoundary` is a dedicated class that manages `@Transactional` boundaries and translates infrastructure exceptions into domain exceptions. It inspects the constraint name inside `DataIntegrityViolationException` (via Hibernate's `ConstraintViolationException.getConstraintName()`) to distinguish between different constraint violations — for example, mapping `uq_coupon_usages_code_user_id` to `CouponAlreadyUsedByUserException` while letting an FK violation propagate as-is. This keeps `CouponService` (application layer) free of Spring and JPA annotations, and `CouponUseCaseImpl` (orchestrator) focused on coordinating steps rather than managing transaction scope or exception translation.

It also makes transaction boundaries explicit and testable. You can mock the boundary in unit tests to verify that the orchestrator calls the right transactional operations in the right order.

### Coupon usage flow and TOCTOU

The "use coupon" flow runs three steps in this order:

1. `validateUserNotUsed()` - cheap read-only DB check (own transaction)
2. `geoLocationProvider.resolveCountry()` - external REST call to ip-api.com (with retry + circuit breaker, potentially slow)
3. `executeUsage()` - transactional mutation: find coupon, validate, increment usage, record usage, save

This ordering introduces a theoretical TOCTOU (Time-of-Check-Time-of-Use) race condition between steps 1 and 3. This is a deliberate choice for two reasons:

- If the user has already redeemed the coupon, we reject immediately without wasting a REST call to the geolocation service (fail-fast before expensive I/O).
- Placing the REST call (which may retry with backoff) inside a transaction would hold a HikariCP connection for seconds, risking pool starvation under load (pool size is 20).

Correctness is guaranteed regardless. `executeUsage()` catches `DataIntegrityViolationException` and checks the constraint name via `ConstraintViolationException.getConstraintName()`. Only `uq_coupon_usages_code_user_id` is mapped to `CouponAlreadyUsedByUserException` — any other constraint violation (e.g. the FK `fk_coupon_usages_code` if the coupon was deleted between steps) propagates unchanged, avoiding a misleading error message.

### `saveAndFlush` over `save`

Repository adapters use `saveAndFlush()` instead of `save()` to force immediate SQL execution within the `CouponTransactionBoundary` try-catch blocks. Without the flush, Hibernate could batch the INSERT/UPDATE until commit time, and the resulting `DataIntegrityViolationException` or `OptimisticLockingFailureException` would escape the catch and surface as an unhandled 500 instead of a clean 409.

### Concurrency

`@Version` on the `coupons` table prevents lost updates when multiple users redeem simultaneously. `OptimisticLockingFailureException` is translated to a 409 Conflict asking the client to retry.

A unique constraint on `(code, user_id)` in `coupon_usages` enforces one-use-per-user at the DB level. A foreign key from `coupon_usages.code` to `coupons.code` with `ON DELETE CASCADE` ensures that deleting a coupon automatically removes all associated usage records.

Coupon creation has a similar TOCTOU window: `existsByCode` can pass for two concurrent requests with the same code, but only one INSERT will succeed. `CouponTransactionBoundary.save()` catches the resulting `DataIntegrityViolationException`, verifies the constraint name is `uk_coupons_code`, and maps it to `CouponAlreadyExistsException` (409).

The FK uses database-level cascading rather than JPA `orphanRemoval`. Adding `orphanRemoval = true` would require a `@OneToMany` collection on `CouponEntity`, which means Hibernate would eagerly or lazily load all usage records every time a coupon is fetched. For a popular coupon with thousands of redemptions, that is wasteful. Since usages are managed through their own repository (`CouponUsageRepository`) and never accessed as a collection on the aggregate, database-level `ON DELETE CASCADE` is the right tool: it handles cleanup without polluting the read path.

### Resilience as a decorator

The geolocation adapter is wrapped in `ResilientGeoLocationAdapter` (decorator pattern) that adds retry (3 attempts, 50ms backoff) on network errors and a circuit breaker (opens after 50% failure rate over 10 calls, 30s recovery window).

This keeps resilience concerns out of the domain and application layers. The decorator delegates to the actual `GeoLocationAdapter`, both implement the same `GeoLocationProvider` port. Neither class is auto-discovered via `@Component` — both are wired explicitly through `@Bean` methods in `BeanConfiguration`, which creates `GeoLocationAdapter` as the delegate and exposes `ResilientGeoLocationAdapter` as the `GeoLocationProvider` bean. This makes the decoration chain visible in one place.

### Exception hierarchy for selective retry

The geolocation layer uses three exception classes:

- `GeoLocationException` — base class for logical failures (invalid IP, API returning `"fail"` status). Maps to 400. Not retried, not recorded by the circuit breaker.
- `GeoLocationNetworkException` extends `GeoLocationException` — wraps transient network errors (`ResourceAccessException`, `RestClientException`). Triggers retry (3 attempts, 50ms backoff) and is recorded by the circuit breaker. Maps to 503.
- `GeoLocationUnavailableException` extends `GeoLocationException` — thrown by the circuit breaker fallback when the breaker is open. Not a subclass of `GeoLocationNetworkException`, so it does not trigger retry and is not recorded by the circuit breaker (which would be circular). Maps to 503.

This separation ensures that retries only fire on transient errors, the circuit breaker only counts actual network failures, and an open breaker produces a clean 503 rather than a misleading 400.

### Interface segregation on use case ports

`CreateCouponUseCase` and `UseCouponUseCase` are separate interfaces even though `CouponUseCaseImpl` implements both. The controller injects only the port it needs. Any future consumer could depend on just one without pulling in the other.

### Correlation IDs and structured logging

`CorrelationIdFilter` runs first in the servlet chain (order `HIGHEST_PRECEDENCE`, using `OncePerRequestFilter`). It reads the `X-Request-Id` header from the incoming request or generates a fresh UUID when absent, places it into SLF4J MDC under key `requestId`, and echoes it back on the response so upstream systems can join their logs to ours. MDC is cleared in a `finally` block, which keeps things correct with virtual threads and any pool that recycles carrier threads.

The Spring Boot log pattern is customized via `logging.pattern.correlation` — a dedicated slot in the default Logback layout that Spring Boot exposes for exactly this purpose. Every log line (application, framework or third-party) carries `[requestId=...]` without touching individual log statements and without a custom `logback-spring.xml` — the built-in `defaults.xml` interpolates `LOG_CORRELATION_PATTERN` from the property.

### Request validation and error responses

DTOs use Bean Validation (`@NotBlank`, `@Size(max = 100)` on `code`, `@Min(1) @Max(1_000_000)` on `maxUsages`, `@Pattern` on `country`). Constraint violations produce `MethodArgumentNotValidException`, which `GlobalExceptionHandler.handleMethodArgumentNotValid` overrides to build an RFC 7807 `ProblemDetail` where `detail` joins every field error as `"field: message"`, separated by `"; "`. Example: `"code: Coupon code must be at most 100 characters; maxUsages: Max usages must not exceed 1000000"`.

Domain and application exceptions map to specific HTTP statuses (404, 409, 403, 503) through dedicated `@ExceptionHandler` methods, all producing `ProblemDetail`.

### Enforced architecture with ArchUnit

`HexagonalArchitectureTest` runs at every build and covers three groups of rules:

- **Layer dependencies**: `domain` may not depend on `application`, `infrastructure` or `presentation`; `application` may not depend on `infrastructure` or `presentation`; `presentation` may not depend on `infrastructure`. A `layeredArchitecture()` rule enforces the whole graph in one place.
- **Framework isolation**: `domain` must not depend on Spring, JPA, Hibernate, Jackson, Swagger, servlet API or Jakarta Validation; Spring stereotypes (`@Component`, `@Service`, `@Repository`, `@RestController`) may not appear in `domain` or `application`.
- **Package placement**: `@Entity`-annotated classes must live only in `..infrastructure.persistence.entity..`; naming conventions for controllers, services, adapters, entities, mappers, DTOs, exceptions and configuration classes.

The architecture is not just documented — a wrong dependency fails the build.

### Other notes

`open-in-view` is set to `false` to disable the Open Session in View anti-pattern. Lazy loading outside a transaction fails immediately instead of silently causing N+1 queries.

Virtual threads are enabled (`spring.threads.virtual.enabled: true`) since this workload is mostly blocking I/O (DB queries + REST calls).

`ddl-auto: validate` means Hibernate validates the schema against entities at startup but never modifies it. All DDL is managed by Liquibase.

`lombok.addLombokGeneratedAnnotation = true` in `lombok.config` makes JaCoCo correctly exclude Lombok-generated code from coverage reports.

`GeoLocationAdapter` validates that the IP is public (not loopback, site-local or link-local) before calling the external API, so we fail fast without wasting external resources.

### CI/CD and Kubernetes

The project uses a two-stage GitHub Actions pipeline. The CI workflow runs on every push and pull request to `master`: it compiles, runs all tests (unit + integration via Testcontainers), and enforces JaCoCo coverage at 80% minimum. The Deploy workflow triggers only after CI succeeds (`workflow_run`), builds a Docker image, pushes it to GHCR, and deploys to OpenShift.

The deploy job checks out the exact commit that CI tested (`workflow_run.head_sha`) to prevent deploying an untested revision when fast pushes occur.

Kubernetes manifests include security hardening: pods run as non-root with `allowPrivilegeEscalation: false`, all Linux capabilities dropped, and `readOnlyRootFilesystem` on the application container (with an `emptyDir` tmpfs for `/tmp`). A `NetworkPolicy` restricts MySQL ingress to only pods labeled `coupon-service`, and a `PodDisruptionBudget` ensures at least one replica stays available during voluntary disruptions.

MySQL runs as a `StatefulSet` with a `gp3` EBS PersistentVolumeClaim. The `innodb-buffer-pool-size` is set to 128M (25% of the 512M memory limit), leaving headroom for connection buffers and OS overhead.

### Intentionally omitted

Some concerns were left out to keep the scope focused on domain modeling, architecture and concurrency handling:

**Authentication and authorization** - in production the endpoints would be secured with JWT or OAuth2 for the usage endpoint, and API key or role-based access for coupon creation. Left out to avoid obscuring the core domain logic with security boilerplate.

**Rate limiting** - a production deployment would include rate limiting (Bucket4j, API gateway throttling or similar) to protect both the service and the downstream geolocation API. This is an infrastructure concern orthogonal to the business logic this project demonstrates.

**`X-Forwarded-For` handling** - client IP resolution relies on Spring Boot's `forward-headers-strategy: native`, which delegates to Tomcat's `RemoteIpValve`. The valve processes the `X-Forwarded-For` header and sets `request.getRemoteAddr()` to the real client IP, so the controller does not parse the header manually. Trusted proxy IPs are configured via the `TRUSTED_PROXIES` environment variable (`server.tomcat.remoteip.internal-proxies`), defaulting to standard private ranges (10.x, 172.16-31.x, 192.168.x, 127.x, ::1). In a shared cluster this should be narrowed to the OpenShift router CIDR to prevent other pods from spoofing the header. The existing `NetworkPolicy` limits ingress to the router, which mitigates this risk at the network level.

Integration tests for the usage endpoint use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `RestTestClient` instead of `MockMvc`. This is a deliberate choice: `MockMvc` uses a mock servlet environment that does not activate Tomcat's `RemoteIpValve`, so `X-Forwarded-For` processing would not be tested. `RANDOM_PORT` starts the real embedded Tomcat, which means the valve runs and the tests verify the full request pipeline including IP resolution. The trade-off is slower test execution, but it ensures the IP handling works end-to-end rather than being silently bypassed in tests.

## Tech Stack

Java 25, Spring Boot 4.1, Spring Data JPA, Hibernate, MySQL 9.6, Liquibase, Resilience4j, Spring Retry, Lombok, SpringDoc OpenAPI, JaCoCo, JUnit 5, Mockito, Testcontainers, ArchUnit, Docker (multi-stage build), Kubernetes / OpenShift (AWS EBS gp3), GitHub Actions CI/CD.

## Running Locally

Prerequisites: Java 25+, Docker and Docker Compose.

```bash
cp .env.example .env
# fill in credentials in .env
docker compose up -d
```

The service starts at `http://localhost:8080`. Swagger UI is available at `/swagger-ui.html` when `SPRINGDOC_ENABLED=true`.

### Tests

```bash
./mvnw test        # unit tests
./mvnw verify      # unit + integration tests (requires Docker for Testcontainers) + JaCoCo coverage check (80% min)
```

Coverage report is generated at `target/site/jacoco/index.html`.

## API

Every response includes an `X-Request-Id` header — either the one the caller sent or a fresh UUID minted by `CorrelationIdFilter`. The same ID appears on every log line produced during the request.

### Create Coupon

```
POST /coupons
Content-Type: application/json
X-Request-Id: 8f1e...            # optional; generated if absent

{"code": "SUMMER26", "maxUsages": 100, "country": "PL"}
```

Validation errors return `400` with an RFC 7807 body:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "code: Coupon code must be at most 100 characters; maxUsages: Max usages must not exceed 1000000"
}
```

### Use Coupon

```
POST /coupons/{code}/usages
Content-Type: application/json
X-Forwarded-For: 89.64.55.1
X-Request-Id: 8f1e...            # optional

{"userId": "user-123"}
```