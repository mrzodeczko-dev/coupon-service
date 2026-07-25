package com.rzodeczko;

import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.model.Country;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Concurrency tests for the coupon service. Fires many parallel requests through the real
 * Tomcat + HikariCP + MySQL stack and asserts the count of successes vs conflicts is exact.
 * <p>
 * These tests exercise the primitives the design leans on:
 * <ul>
 *   <li>{@code uq_coupon_usages_code_user_id} + exception translation (one-use-per-user)</li>
 *   <li>{@code @Version} optimistic lock + {@code CouponExhaustedException} (usage cap)</li>
 *   <li>{@code uk_coupons_code} + exception translation (unique code on create)</li>
 * </ul>
 * They are the difference between "we wrote a race-safe design" and "we proved it holds".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM coupon_usages", "DELETE FROM coupons"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CouponConcurrencyIntegrationTest {

    private static final String IP = "89.64.55.1";

    @Autowired
    private RestTestClient restTestClient;

    @MockitoBean
    private GeoLocationProvider geoLocationProvider;

    @Test
    void shouldAllowExactlyOneRedemptionWhenSameUserRequestsInParallel() throws Exception {
        given(geoLocationProvider.resolveCountry(IP)).willReturn(new Country("PL"));

        createCoupon("RACE_SAME_USER", 100, "PL");

        int parallelRequests = 10;
        List<HttpStatusCode> statuses = fireInParallel(
                parallelRequests,
                () -> useCoupon("RACE_SAME_USER", "user-1")
        );

        long successes = statuses.stream().filter(HttpStatusCode::is2xxSuccessful).count();
        long conflicts = statuses.stream().filter(s -> s.value() == HttpStatus.CONFLICT.value()).count();

        assertThat(successes).as("exactly one redemption should succeed").isEqualTo(1);
        assertThat(conflicts).as("all remaining requests should be rejected with 409").isEqualTo(parallelRequests - 1);
        assertThat(statuses)
                .as("no request should fail with 500 or any status other than 200/409")
                .allMatch(s -> s.is2xxSuccessful() || s.value() == HttpStatus.CONFLICT.value());
    }

    /**
     * Correctness invariant under contention: never oversell.
     * <p>
     * With optimistic locking (@Version) and no server-side retry, only a subset of the
     * parallel requests will win the race  - the rest see OptimisticLockingFailureException
     * and get 409 (CouponConcurrentModificationException). The exception message even tells
     * the client to retry. So the *guaranteed* property is not "exactly maxUsages succeed
     * in one wave", it's "no more than maxUsages ever succeed, and no request is lost to 500".
     * <p>
     * If server-side retry-on-optimistic-lock were added later, this test could be tightened
     * to {@code successes == maxUsages}.
     */
    @Test
    void shouldNeverOversellUnderParallelDistinctUsers() throws Exception {
        given(geoLocationProvider.resolveCountry(IP)).willReturn(new Country("PL"));

        int maxUsages = 5;
        int parallelUsers = 20;
        createCoupon("RACE_CAP", maxUsages, "PL");

        List<HttpStatusCode> statuses = fireInParallelIndexed(
                parallelUsers,
                i -> useCoupon("RACE_CAP", "user-" + i)
        );

        long successes = statuses.stream().filter(HttpStatusCode::is2xxSuccessful).count();

        assertThat(successes)
                .as("must never oversell: successes in [1, maxUsages]")
                .isBetween(1L, (long) maxUsages);
        assertThat(statuses)
                .as("every request must resolve to 200 or 409  - no 500s, no leaks")
                .allMatch(s -> s.is2xxSuccessful() || s.value() == HttpStatus.CONFLICT.value());
    }

    @Test
    void shouldPersistExactlyOneCouponWhenSameCodeCreatedInParallel() throws Exception {
        int parallelRequests = 10;

        List<HttpStatusCode> statuses = fireInParallel(
                parallelRequests,
                () -> createCouponRaw("RACE_CREATE", 5, "PL")
        );

        long created = statuses.stream().filter(s -> s.value() == HttpStatus.CREATED.value()).count();
        long conflicts = statuses.stream().filter(s -> s.value() == HttpStatus.CONFLICT.value()).count();

        assertThat(created).as("exactly one create should succeed").isEqualTo(1);
        assertThat(conflicts).as("remaining creates should be rejected with 409")
                .isEqualTo(parallelRequests - 1);
        assertThat(statuses)
                .as("no request should fail with 500 or any status other than 201/409")
                .allMatch(s -> s.value() == HttpStatus.CREATED.value()
                        || s.value() == HttpStatus.CONFLICT.value());
    }

    // ------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------

    private void createCoupon(String code, int maxUsages, String country) {
        createCouponRaw(code, maxUsages, country);
    }

    private HttpStatusCode createCouponRaw(String code, int maxUsages, String country) {
        return restTestClient.post().uri("/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .body(("""
                        {"code":"%s","maxUsages":%d,"country":"%s"}
                        """).formatted(code, maxUsages, country))
                .exchange()
                .returnResult(Void.class)
                .getStatus();
    }

    private HttpStatusCode useCoupon(String code, String userId) {
        return restTestClient.post().uri("/v1/coupons/{code}/usages", code)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", IP)
                .body(("""
                        {"userId":"%s"}
                        """).formatted(userId))
                .exchange()
                .returnResult(Void.class)
                .getStatus();
    }

    /**
     * Fires {@code count} identical calls in parallel, releasing them together via a latch
     * so contention is real (not staggered by executor ramp-up).
     */
    private List<HttpStatusCode> fireInParallel(int count, StatusCall call) throws Exception {
        return fireInParallelIndexed(count, i -> call.execute());
    }

    private List<HttpStatusCode> fireInParallelIndexed(int count, IndexedStatusCall call) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger();

            List<CompletableFuture<HttpStatusCode>> futures = IntStream.range(0, count)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            startLatch.await();
                            return call.execute(i);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        } finally {
                            completed.incrementAndGet();
                        }
                    }, executor))
                    .toList();

            startLatch.countDown();
            CompletableFuture
                    .allOf(futures.toArray(CompletableFuture[]::new))
                    .get(30, TimeUnit.SECONDS);

            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface StatusCall {
        HttpStatusCode execute();
    }

    @FunctionalInterface
    private interface IndexedStatusCall {
        HttpStatusCode execute(int index);
    }
}
