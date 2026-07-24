# Coupon Service

REST API for creating and redeeming discount coupons with country-based restrictions. Built with Spring Boot 4.1 and Java 25.

## Features

- Create coupons with a unique code, usage limit and country restriction (ISO 3166-1 alpha-2)
- Redeem coupons with per-user uniqueness enforcement and IP-based geolocation verification
- Optimistic locking for safe concurrent access
- Circuit breaker + retry on the geolocation provider (Resilience4j)
- Liquibase database migrations
- OpenAPI / Swagger UI documentation
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

`CouponTransactionBoundary` is a dedicated class whose only job is annotating methods with `@Transactional`. This keeps `CouponService` (application layer) free of Spring annotations, and `CouponUseCaseImpl` (orchestrator) focused on coordinating steps rather than managing transaction scope.

It also makes transaction boundaries explicit and testable. You can mock the boundary in unit tests to verify that the orchestrator calls the right transactional operations in the right order.

### Coupon usage flow and TOCTOU

The "use coupon" flow runs three steps in this order:

1. `validateUserNotUsed()` - cheap read-only DB check (own transaction)
2. `geoLocationProvider.resolveCountry()` - external REST call to ip-api.com (with retry + circuit breaker, potentially slow)
3. `executeUsage()` - transactional mutation: find coupon, validate, increment usage, record usage, save

This ordering introduces a theoretical TOCTOU (Time-of-Check-Time-of-Use) race condition between steps 1 and 3. This is a deliberate choice for two reasons:

- If the user has already redeemed the coupon, we reject immediately without wasting a REST call to the geolocation service (fail-fast before expensive I/O).
- Placing the REST call (which may retry with backoff) inside a transaction would hold a HikariCP connection for seconds, risking pool starvation under load (pool size is 20).

Correctness is guaranteed regardless. `executeUsage()` catches `DataIntegrityViolationException` from the `UNIQUE(code, user_id)` constraint on `coupon_usages`, so a concurrent duplicate is always rejected at the database level.

### `saveAndFlush` over `save`

Repository adapters use `saveAndFlush()` instead of `save()` to force immediate SQL execution within the `CouponTransactionBoundary` try-catch blocks. Without the flush, Hibernate could batch the INSERT/UPDATE until commit time, and the resulting `DataIntegrityViolationException` or `OptimisticLockingFailureException` would escape the catch and surface as an unhandled 500 instead of a clean 409.

### Concurrency

`@Version` on the `coupons` table prevents lost updates when multiple users redeem simultaneously. `OptimisticLockingFailureException` is translated to a 409 Conflict asking the client to retry.

A unique constraint on `(code, user_id)` in `coupon_usages` enforces one-use-per-user at the DB level. A foreign key from `coupon_usages.code` to `coupons.code` with `ON DELETE CASCADE` ensures that deleting a coupon automatically removes all associated usage records.

The FK uses database-level cascading rather than JPA `orphanRemoval`. Adding `orphanRemoval = true` would require a `@OneToMany` collection on `CouponEntity`, which means Hibernate would eagerly or lazily load all usage records every time a coupon is fetched. For a popular coupon with thousands of redemptions, that is wasteful. Since usages are managed through their own repository (`CouponUsageRepository`) and never accessed as a collection on the aggregate, database-level `ON DELETE CASCADE` is the right tool: it handles cleanup without polluting the read path.

### Resilience as a decorator

The geolocation adapter is wrapped in `ResilientGeoLocationAdapter` (decorator pattern) that adds retry (3 attempts, 50ms backoff) on network errors and a circuit breaker (opens after 50% failure rate over 10 calls, 30s recovery window).

This keeps resilience concerns out of the domain and application layers. The decorator delegates to the actual `GeoLocationAdapter`, both implement the same `GeoLocationProvider` port. Neither class is auto-discovered via `@Component` — both are wired explicitly through `@Bean` methods in `BeanConfiguration`, which creates `GeoLocationAdapter` as the delegate and exposes `ResilientGeoLocationAdapter` as the `GeoLocationProvider` bean. This makes the decoration chain visible in one place.

### Exception hierarchy for selective retry

`GeoLocationNetworkException` extends `GeoLocationException`. Only the network subclass (wrapping `ResourceAccessException`, `RestClientException`) triggers retry. Logical failures like invalid IP or API returning `"fail"` status throw the base `GeoLocationException` and are not retried, since repeating the same request would produce the same result.

Same applies to the circuit breaker: only `GeoLocationNetworkException` is recorded as a failure. A bad IP address does not degrade the circuit breaker health metrics.

### Interface segregation on use case ports

`CreateCouponUseCase` and `UseCouponUseCase` are separate interfaces even though `CouponUseCaseImpl` implements both. The controller injects only the port it needs. Any future consumer could depend on just one without pulling in the other.

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

**`X-Forwarded-For` spoofing protection** - the current implementation trusts the first IP in the header. In production this would be hardened with a trusted proxy list or handled at the reverse proxy / API gateway layer.

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

### Create Coupon

```
POST /coupons
Content-Type: application/json

{"code": "SUMMER25", "maxUsages": 100, "country": "PL"}
```

### Use Coupon

```
POST /coupons/{code}/usages
Content-Type: application/json
X-Forwarded-For: 89.64.55.1

{"userId": "user-123"}
```