# Coupon Service

REST API for creating and redeeming discount coupons with country-based restrictions. Built with Spring Boot 4.1 and Java 25.

## Features

- Create coupons with a unique code, usage limit and country restriction (ISO 3166-1 alpha-2)
- Redeem coupons with per-user uniqueness enforcement and IP-based geolocation verification
- Pluggable geolocation providers: ip-api.com (HTTP, default) and FindIP.net (HTTPS), switchable via configuration
- VPN/proxy/Tor/hosting detection on both providers
- Optimistic locking for safe concurrent access
- Circuit breaker + retry on the geolocation provider (Resilience4j)
- Liquibase database migrations
- OpenAPI / Swagger UI documentation
- Native API versioning via Spring Boot 4's built-in path-segment strategy
- Request-level correlation IDs propagated through MDC and echoed on responses
- Field-level validation errors surfaced through RFC 9457 ProblemDetail
- ArchUnit tests enforcing hexagonal layer boundaries
- JaCoCo code coverage enforcement (80% minimum)
- CI/CD pipeline: GitHub Actions -> GHCR -> OpenShift
- Kubernetes manifests with security hardening and NetworkPolicy

## Architecture

The project follows Hexagonal Architecture (Ports & Adapters):

```
domain/          Pure Java domain model, no framework dependencies
application/     Use case ports (input/output) and application service
infrastructure/  Spring-managed adapters: JPA, geolocation REST clients,
                 transaction boundaries, resilience decorator, configuration
presentation/    REST controllers, DTOs, global exception handler
```

All dependencies point inward. Domain knows nothing about Spring, JPA or HTTP. Application defines port interfaces but has no idea who implements them. Infrastructure and presentation depend on the inner layers, never the other way around.

There are two kinds of ports. Input ports (`CreateCouponUseCase`, `UseCouponUseCase`) define what the application can do. The controller calls them without knowing the implementation. Output ports (`CouponRepository`, `CouponUsageRepository`, `GeoLocationProvider`) define what the application needs from the outside world. Infrastructure provides the adapters: `JpaCouponRepositoryAdapter` implements `CouponRepository`, `IpApiGeoLocationAdapter` and `FindIpGeoLocationAdapter` implement `GeoLocationProvider`, and so on.

`CouponUseCaseImpl` lives in the infrastructure package, not in application. It depends on Spring (`@Component`, `@Slf4j`) and on `CouponTransactionBoundary` which is a Spring-managed transactional wrapper, so it does not belong in the application layer. The application layer contains only the port interfaces, command records and `CouponService` (framework-free, instantiated manually via `@Bean`).

The adapter layer has an extra mapping step between domain and persistence. `CouponMapper` converts between `Coupon` (domain) and `CouponEntity` (JPA), so Hibernate annotations never leak into the domain model and DB schema changes don't force changes in domain classes. The JPA repositories (`JpaCouponRepository`) are standard Spring Data interfaces, hidden behind the adapter and never referenced outside of infrastructure.

## Design Decisions

### Framework-free domain and application layers

`CouponService` is a plain Java class with no `@Service`, no `@Component`, no Spring imports. It gets instantiated via an explicit `@Bean` method in `BeanConfiguration`. The entire domain and application layer can be unit-tested without a Spring context and could be reused in a non-Spring runtime.

The domain model is no different: `Coupon`, `CouponCode`, `Country` and all domain exceptions are pure Java with no framework dependencies.

### Rich domain model with self-validating Value Objects

`CouponCode` and `Country` are Java records with validation in their compact constructors. You simply cannot create an instance of `Country` with an invalid ISO code or a blank `CouponCode`. Invariants are enforced at construction time, not scattered across service methods.

`Coupon` uses factory methods to separate creation from reconstitution. `Coupon.create()` generates a new ID, sets the timestamp and validates `maxUsages > 0`. `Coupon.reconstitute()` rebuilds from persisted state, trusting the data. Persistence adapters cannot accidentally bypass domain rules because there is no public constructor.

Equality is based solely on the aggregate ID (`UUID`), following the DDD entity identity pattern.

### Transaction Boundary pattern

`CouponTransactionBoundary` manages `@Transactional` boundaries and translates infrastructure exceptions into domain exceptions. It inspects the constraint name inside `DataIntegrityViolationException` (via Hibernate's `ConstraintViolationException.getConstraintName()`) to distinguish between different constraint violations, for example mapping `uq_coupon_usages_code_user_id` to `CouponAlreadyUsedByUserException` while letting an FK violation propagate as-is. `CouponService` (application layer) stays free of Spring and JPA annotations, and `CouponUseCaseImpl` (orchestrator) only coordinates steps without managing transaction scope or exception translation.

Transaction boundaries are explicit and testable. You can mock the boundary in unit tests to verify that the orchestrator calls the right transactional operations in the right order.

### Coupon usage flow and TOCTOU

The "use coupon" flow runs three steps in this order:

1. `validateUserNotUsed()` - cheap read-only DB check (own transaction)
2. `geoLocationProvider.resolveCountry()` - external REST call to the configured provider (with retry + circuit breaker, potentially slow)
3. `executeUsage()` - transactional mutation: find coupon, validate, increment usage, record usage, save

There is a theoretical TOCTOU (Time-of-Check-Time-of-Use) race condition between steps 1 and 3. Two reasons why this ordering was chosen anyway:

- If the user has already redeemed the coupon, we reject immediately without wasting a REST call to the geolocation service (fail-fast before expensive I/O).
- Placing the REST call (which may retry with backoff) inside a transaction would hold a HikariCP connection for seconds, risking pool starvation under load (pool size is 20).

Correctness holds regardless. `executeUsage()` catches `DataIntegrityViolationException` and checks the constraint name via `ConstraintViolationException.getConstraintName()`. Only `uq_coupon_usages_code_user_id` is mapped to `CouponAlreadyUsedByUserException`. Any other constraint violation (e.g. the FK `fk_coupon_usages_code` if the coupon was deleted between steps) propagates unchanged, avoiding a misleading error message.

### `saveAndFlush` over `save`

Repository adapters use `saveAndFlush()` instead of `save()` to force immediate SQL execution within the `CouponTransactionBoundary` try-catch blocks. Without the flush, Hibernate could batch the INSERT/UPDATE until commit time, and the resulting `DataIntegrityViolationException` or `OptimisticLockingFailureException` would escape the catch and surface as an unhandled 500 instead of a clean 409.

### Concurrency

`@Version` on the `coupons` table prevents lost updates when multiple users redeem simultaneously. `OptimisticLockingFailureException` is translated to a 409 Conflict asking the client to retry.

A unique constraint on `(code, user_id)` in `coupon_usages` enforces one-use-per-user at the DB level. A foreign key from `coupon_usages.code` to `coupons.code` with `ON DELETE CASCADE` ensures that deleting a coupon automatically removes all associated usage records.

Coupon creation has a similar TOCTOU window: `existsByCode` can pass for two concurrent requests with the same code, but only one INSERT will succeed. `CouponTransactionBoundary.save()` catches the resulting `DataIntegrityViolationException`, verifies the constraint name is `uk_coupons_code`, and maps it to `CouponAlreadyExistsException` (409).

The FK uses database-level cascading rather than JPA `orphanRemoval`. Adding `orphanRemoval = true` would require a `@OneToMany` collection on `CouponEntity`, which means Hibernate would eagerly or lazily load all usage records every time a coupon is fetched. For a popular coupon with thousands of redemptions, that is wasteful. Since usages are managed through their own repository (`CouponUsageRepository`) and never accessed as a collection on the aggregate, database-level `ON DELETE CASCADE` is the right tool: it handles cleanup without polluting the read path.

### Pluggable geolocation providers

The service supports two geolocation providers behind the `GeoLocationProvider` port:

- `IpApiGeoLocationAdapter` - calls `http://ip-api.com/json/{ip}` over HTTP. Returns country via `countryCode`, detects proxy/hosting via dedicated fields. This is the default provider, works out of the box without any API token.
- `FindIpGeoLocationAdapter` - calls `https://api.findip.net/{ip}/?token={token}` over HTTPS. Returns country via `country.iso_code`, detects VPN/proxy/Tor/hosting via `intelligence.flags`. Requires a token; intended for production or environments where HTTPS and richer detection matter.

Both adapters share IP address validation logic through `IpAddressValidator`, a utility class in `infrastructure.geolocation.validation` that rejects loopback, site-local and link-local addresses before any external call is made.

Provider selection is controlled by `GeoLocationProperties`, a `@ConfigurationProperties` record with a `Provider` enum (`IP_API`, `FINDIP`). Spring Boot's relaxed binding maps the YAML value `findip` to `Provider.FINDIP` and `ip-api` to `Provider.IP_API`. The enum gives type safety and fail-fast behavior: a typo in the property (e.g. `find-ip`) causes a binding error at startup instead of a silent fallback.

`BeanConfiguration` reads the enum and uses a `switch` expression to instantiate the correct adapter. For FindIP, the token is validated eagerly via `FindIp.requireToken()`, which throws `IllegalStateException` with a clear message if the token is missing or blank. The raw adapter is then wrapped in `ResilientGeoLocationAdapter` (marked `@Primary`) so the circuit breaker and retry logic apply regardless of which provider is active.

Configuration in `application.yaml`:

```yaml
geolocation:
  provider: ${GEOLOCATION_PROVIDER:ip-api}
  findip:
    token: ${FINDIP_API_TOKEN:}
```

On OpenShift, `GEOLOCATION_PROVIDER` is set in the ConfigMap (overridden to `findip` for production) and `FINDIP_API_TOKEN` in the Secret. Switching providers is a ConfigMap change + rolling restart.

### Resilience as a decorator

The geolocation adapter is wrapped in `ResilientGeoLocationAdapter` (decorator pattern) that adds retry (3 attempts, 50ms backoff) on network errors and a circuit breaker (opens after 50% failure rate over 10 calls, 30s recovery window).

Resilience concerns stay out of the domain and application layers. The decorator delegates to whichever adapter `BeanConfiguration` selected, and both implement the same `GeoLocationProvider` port. Neither the adapters nor the decorator is auto-discovered via `@Component`. All are wired explicitly through `@Bean` methods in `BeanConfiguration`: a `switch` creates the raw adapter based on the configured provider, and a second `@Primary` bean wraps it in `ResilientGeoLocationAdapter`. The decoration chain is visible in one place.

### Exception hierarchy for selective retry

The geolocation layer uses four exception classes:

- `GeoLocationException` - base class for logical failures (invalid IP, API returning an error status). Maps to 400. Not retried, not recorded by the circuit breaker.
- `GeoLocationNetworkException` extends `GeoLocationException` - wraps transient network errors (`ResourceAccessException`, `RestClientException`). Triggers retry (3 attempts, 50ms backoff) and is recorded by the circuit breaker. Maps to 503.
- `GeoLocationUnavailableException` extends `GeoLocationException` - thrown by the circuit breaker fallback when the breaker is open. Not a subclass of `GeoLocationNetworkException`, so it does not trigger retry and is not recorded by the circuit breaker (which would be circular). Maps to 503.
- `VpnDetectedException` extends `GeoLocationException` - thrown by both adapters when the IP is flagged as VPN, proxy, Tor or hosting provider. Maps to 403 Forbidden. Not retried, not recorded by the circuit breaker.

Retries only fire on transient errors, the circuit breaker only counts actual network failures, VPN detection gives a clear 403, and an open breaker produces a clean 503 rather than a misleading 400.

### Testing the resilience layer

Two test classes cover the resilience decorator:

`ResilientGeoLocationAdapterTest` is a unit test with no Spring context. It checks delegation to the underlying provider and calls the fallback method directly with a `CallNotPermittedException`, verifying that `GeoLocationUnavailableException` is thrown with the IP in the message. Without AOP proxy, retry and circuit breaker annotations have no effect here.

`ResilientGeoLocationAdapterIT` is an integration test that boots Spring with Resilience4j and Spring Retry auto-configured. The `ResilientGeoLocationAdapter` bean gets a real CGLIB proxy with both aspects active. Uses a tight circuit breaker config (sliding window of 5, minimum 3 calls, 50% threshold) so state transitions happen fast. Covers: 3 retries on `GeoLocationNetworkException`, no retry on plain `GeoLocationException`, circuit breaker opening after enough failures and rejecting with `GeoLocationUnavailableException`, staying closed below the threshold, and ignoring non-network exceptions in the failure rate.

The mock delegate is a static field, not a Spring bean. If it were registered as a bean implementing `GeoLocationProvider`, Spring would proxy it or confuse it with the `ResilientGeoLocationAdapter` bean (same interface), and Mockito's `reset()` would fail with `NotAMockException`. Keeping the mock outside the application context avoids the problem.

### Interface segregation on use case ports

`CreateCouponUseCase` and `UseCouponUseCase` are separate interfaces even though `CouponUseCaseImpl` implements both. The controller injects only the port it needs. Any future consumer could depend on just one without pulling in the other.

### Correlation IDs and structured logging

`CorrelationIdFilter` runs first in the servlet chain (order `HIGHEST_PRECEDENCE`, using `OncePerRequestFilter`). It reads the `X-Request-Id` header from the incoming request or generates a fresh UUID when absent, places it into SLF4J MDC under key `requestId`, and echoes it back on the response so upstream systems can join their logs to ours. MDC is cleared in a `finally` block, which keeps things correct with virtual threads and any pool that recycles carrier threads.

The Spring Boot log pattern is customized via `logging.pattern.correlation`, a dedicated slot in the default Logback layout that Spring Boot exposes for exactly this purpose. Every log line (application, framework or third-party) carries `[requestId=...]` without touching individual log statements and without a custom `logback-spring.xml`. The built-in `defaults.xml` interpolates `LOG_CORRELATION_PATTERN` from the property.

### Request validation and error responses

DTOs use Bean Validation (`@NotBlank`, `@Size(max = 100)` on `code`, `@Min(1) @Max(1_000_000)` on `maxUsages`, `@Pattern` on `country`). Constraint violations produce `MethodArgumentNotValidException`, which `GlobalExceptionHandler.handleMethodArgumentNotValid` overrides to build an RFC 9457 `ProblemDetail` where `detail` joins every field error as `"field: message"`, separated by `"; "`. Example: `"code: Coupon code must be at most 100 characters; maxUsages: Max usages must not exceed 1000000"`.

Malformed JSON bodies produce `HttpMessageNotReadableException`, handled by an override of `handleHttpMessageNotReadable` that returns a 400 ProblemDetail with `"Malformed request body"`.

Domain and application exceptions map to specific HTTP statuses (404, 409, 403, 503) through dedicated `@ExceptionHandler` methods, all producing `ProblemDetail`.

### Enforced architecture with ArchUnit

`HexagonalArchitectureTest` runs at every build and covers three groups of rules:

Layer dependencies: `domain` may not depend on `application`, `infrastructure` or `presentation`; `application` may not depend on `infrastructure` or `presentation`; `presentation` may not depend on `infrastructure`. A `layeredArchitecture()` rule enforces the whole graph in one place.

Framework isolation: `domain` must not depend on Spring, JPA, Hibernate, Jackson, Swagger, servlet API or Jakarta Validation; Spring stereotypes (`@Component`, `@Service`, `@Repository`, `@RestController`) may not appear in `domain` or `application`.

Package placement: `@Entity`-annotated classes must live only in `..infrastructure.persistence.entity..`; naming conventions for controllers, services, adapters, entities, mappers, DTOs, exceptions and configuration classes.

A wrong dependency fails the build.

### API versioning

Spring Boot 4's native API versioning with the path-segment strategy (`spring.mvc.apiversion.use.path-segment=0`). The version is extracted from the first URL segment, so all endpoints are prefixed with `/v1/`. Default version is `1` and `detect-supported: true` means Spring auto-discovers supported versions from `version` attributes on controller mappings, no manual list needed.

Each controller declares `@RequestMapping("/{version}/...")` and individual methods can target specific versions via the `version` attribute on `@PostMapping`, `@GetMapping` etc. Controllers evolve independently: one can serve `v1` while another has moved to `v3`. Adding a new version is a one-line change on the method annotation, no routing config or URL duplication.

### Other notes

`open-in-view` is set to `false` to disable the Open Session in View anti-pattern. Lazy loading outside a transaction fails immediately instead of silently causing N+1 queries.

Virtual threads are enabled (`spring.threads.virtual.enabled: true`) since this workload is mostly blocking I/O (DB queries + REST calls).

`ddl-auto: validate` means Hibernate validates the schema against entities at startup but never modifies it. All DDL is managed by Liquibase.

`lombok.addLombokGeneratedAnnotation = true` in `lombok.config` makes JaCoCo correctly exclude Lombok-generated code from coverage reports.

Both geolocation adapters validate that the IP is public (not loopback, site-local or link-local) via the shared `IpAddressValidator` before calling any external API, so we fail fast without wasting external resources.

### CI/CD and Kubernetes

The project uses a two-stage GitHub Actions pipeline. The CI workflow runs on every push and pull request to `master`: it compiles, runs all tests (unit + integration via Testcontainers), and enforces JaCoCo coverage at 80% minimum. The Deploy workflow triggers after CI succeeds (`workflow_run`) or can be kicked off manually via `workflow_dispatch`. It builds a Docker image, pushes it to GHCR, and deploys to OpenShift.

The deploy job checks out the exact commit that CI tested (`workflow_run.head_sha`) to prevent deploying an untested revision when fast pushes occur.

Kubernetes manifests include security hardening: pods run as non-root with `allowPrivilegeEscalation: false`, all Linux capabilities dropped, and `readOnlyRootFilesystem` on the application container (with an `emptyDir` tmpfs for `/tmp`). A `NetworkPolicy` restricts MySQL ingress to only pods labeled `coupon-service`, and a `PodDisruptionBudget` ensures at least one replica stays available during voluntary disruptions.

MySQL runs as a `StatefulSet` with a `gp3` EBS PersistentVolumeClaim. The `innodb-buffer-pool-size` is set to 128M (25% of the 512M memory limit), leaving headroom for connection buffers and OS overhead.

### Intentionally omitted

A few things were left out to keep the scope on domain modeling, architecture and concurrency:

**Authentication and authorization** - in production the endpoints would be secured with JWT or OAuth2 for the usage endpoint, and API key or role-based access for coupon creation. Left out to avoid obscuring the core domain logic with security boilerplate.

**Rate limiting** - a production deployment would include rate limiting (Bucket4j, API gateway throttling or similar) to protect both the service and the downstream geolocation API. Separate concern from the business logic shown here.

**`X-Forwarded-For` handling** - client IP resolution relies on Spring Boot's `forward-headers-strategy: native`, which delegates to Tomcat's `RemoteIpValve`. The valve processes the `X-Forwarded-For` header and sets `request.getRemoteAddr()` to the real client IP, so the controller does not parse the header manually. Tomcat's default `internal-proxies` pattern already trusts standard private ranges (10.x, 172.16-31.x, 192.168.x, 127.x, ::1). In a shared cluster this should be narrowed to the OpenShift router CIDR via `server.tomcat.remoteip.internal-proxies` to prevent other pods from spoofing the header. The existing `NetworkPolicy` limits ingress to the router, which mitigates this risk at the network level.

Integration tests for the usage endpoint use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `RestTestClient` instead of `MockMvc`. `MockMvc` uses a mock servlet environment that does not activate Tomcat's `RemoteIpValve`, so `X-Forwarded-For` processing would not be tested. `RANDOM_PORT` starts the real embedded Tomcat, so the valve runs and the tests cover the full request pipeline including IP resolution. Slower, but it catches problems that `MockMvc` would silently miss.

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

### Geolocation provider

The default provider is ip-api.com (HTTP, no token required). To switch to FindIP.net (HTTPS), set `FINDIP_API_TOKEN` in your `.env` file and change the provider:

```bash
GEOLOCATION_PROVIDER=findip
FINDIP_API_TOKEN=your-token-here
```

### Tests

```bash
./mvnw test        # unit tests
./mvnw verify      # unit + integration tests (requires Docker for Testcontainers) + JaCoCo coverage check (80% min)
```

Coverage report is generated at `target/site/jacoco/index.html`.

## API

Every response includes an `X-Request-Id` header, either the one the caller sent or a fresh UUID minted by `CorrelationIdFilter`. The same ID appears on every log line produced during the request.

### Create Coupon

```
POST /v1/coupons
Content-Type: application/json
X-Request-Id: 8f1e...            # optional; generated if absent

{"code": "SUMMER26", "maxUsages": 100, "country": "PL"}
```

Validation errors return `400` with an RFC 9457 body:

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
POST /v1/coupons/{code}/usages
Content-Type: application/json
X-Forwarded-For: 89.64.55.1
X-Request-Id: 8f1e...            # optional

{"userId": "user-123"}
```
