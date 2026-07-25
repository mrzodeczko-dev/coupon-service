package com.rzodeczko;

import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.model.Country;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM coupon_usages", "DELETE FROM coupons"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CouponIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @MockitoBean
    private GeoLocationProvider geoLocationProvider;

    @Nested
    class CreateCoupon {

        @Test
        void shouldCreateCouponAndPersist() {
            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"INTEGRATION1","maxUsages":10,"country":"PL"}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.id").isNotEmpty()
                    .jsonPath("$.code").isEqualTo("INTEGRATION1")
                    .jsonPath("$.maxUsages").isEqualTo(10)
                    .jsonPath("$.country").isEqualTo("PL");
        }

        @Test
        void shouldReturn409WhenDuplicateCode() {
            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"DUPLICATE","maxUsages":5,"country":"DE"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"duplicate","maxUsages":5,"country":"DE"}
                            """)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Coupon with code 'DUPLICATE' already exists");
        }

        @Test
        void shouldReturn400WhenInvalidRequest() {
            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"","maxUsages":0,"country":"INVALID"}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    class UseCoupon {

        @Test
        void shouldUseCouponAndDecrementRemaining() {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"USE1","maxUsages":5,"country":"PL"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons/USE1/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"user-1"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("USE1")
                    .jsonPath("$.currentUsages").isEqualTo(1)
                    .jsonPath("$.maxUsages").isEqualTo(5)
                    .jsonPath("$.remainingUsages").isEqualTo(4);
        }

        @Test
        void shouldReturn404WhenCouponDoesNotExist() {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            restTestClient.post().uri("/v1/coupons/NONEXISTENT/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"user-1"}
                            """)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        void shouldReturn403WhenCountryMismatch() {
            given(geoLocationProvider.resolveCountry("8.8.8.8")).willReturn(new Country("US"));

            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"PLONLY","maxUsages":5,"country":"PL"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons/PLONLY/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "8.8.8.8")
                    .body("""
                            {"userId":"user-1"}
                            """)
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        void shouldReturn409WhenCouponExhausted() {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"MAXONE","maxUsages":1,"country":"PL"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons/MAXONE/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"first-user"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons/MAXONE/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"second-user"}
                            """)
                    .exchange()
                    .expectStatus().isEqualTo(409);
        }

        @Test
        void shouldReturn409WhenSameUserUsesAgain() {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"ONEPERUSER","maxUsages":100,"country":"PL"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons/ONEPERUSER/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"user-1"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons/ONEPERUSER/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"user-1"}
                            """)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Coupon 'ONEPERUSER' has already been used by user 'user-1'");
        }

        @Test
        void shouldAllowDifferentUsersToUseSameCoupon() {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"SHARED","maxUsages":100,"country":"PL"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons/SHARED/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"user-1"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.currentUsages").isEqualTo(1);

            restTestClient.post().uri("/v1/coupons/SHARED/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"user-2"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.currentUsages").isEqualTo(2);
        }

        @Test
        void shouldTreatCouponCodeCaseInsensitively() {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"wiosna","maxUsages":10,"country":"PL"}
                            """)
                    .exchange();

            restTestClient.post().uri("/v1/coupons/WIOSNA/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "89.64.55.1")
                    .body("""
                            {"userId":"user-1"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("WIOSNA");
        }
    }

    @Nested
    class CorrelationId {

        @Test
        void shouldEchoIncomingRequestIdInResponse() {
            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", "test-req-999")
                    .body("""
                            {"code":"CID1","maxUsages":1,"country":"PL"}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().valueEquals("X-Request-Id", "test-req-999");
        }

        @Test
        void shouldGenerateRequestIdWhenAbsent() {
            restTestClient.post().uri("/v1/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"CID2","maxUsages":1,"country":"PL"}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().exists("X-Request-Id");
        }
    }
}
