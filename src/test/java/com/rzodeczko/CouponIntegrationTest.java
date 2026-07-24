package com.rzodeczko;

import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.model.Country;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@Sql(statements = {"DELETE FROM coupon_usages", "DELETE FROM coupons"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CouponIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeoLocationProvider geoLocationProvider;

    @Nested
    class CreateCoupon {

        @Test
        void shouldCreateCouponAndPersist() throws Exception {
            mockMvc.perform(post("/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"INTEGRATION1","maxUsages":10,"country":"PL"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.code").value("INTEGRATION1"))
                    .andExpect(jsonPath("$.maxUsages").value(10))
                    .andExpect(jsonPath("$.country").value("PL"));
        }

        @Test
        void shouldReturn409WhenDuplicateCode() throws Exception {
            mockMvc.perform(post("/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"DUPLICATE","maxUsages":5,"country":"DE"}
                            """));

            mockMvc.perform(post("/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"duplicate","maxUsages":5,"country":"DE"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Coupon with code 'DUPLICATE' already exists"));
        }

        @Test
        void shouldReturn400WhenInvalidRequest() throws Exception {
            mockMvc.perform(post("/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"","maxUsages":0,"country":"INVALID"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class UseCoupon {

        @Test
        void shouldUseCouponAndDecrementRemaining() throws Exception {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            mockMvc.perform(post("/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"USE1","maxUsages":5,"country":"PL"}
                            """));

            mockMvc.perform(post("/coupons/USE1/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """)
                            .header("X-Forwarded-For", "89.64.55.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("USE1"))
                    .andExpect(jsonPath("$.currentUsages").value(1))
                    .andExpect(jsonPath("$.maxUsages").value(5))
                    .andExpect(jsonPath("$.remainingUsages").value(4));
        }

        @Test
        void shouldReturn404WhenCouponDoesNotExist() throws Exception {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            mockMvc.perform(post("/coupons/NONEXISTENT/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """)
                            .header("X-Forwarded-For", "89.64.55.1"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403WhenCountryMismatch() throws Exception {
            given(geoLocationProvider.resolveCountry("8.8.8.8")).willReturn(new Country("US"));

            mockMvc.perform(post("/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"PLONLY","maxUsages":5,"country":"PL"}
                            """));

            mockMvc.perform(post("/coupons/PLONLY/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """)
                            .header("X-Forwarded-For", "8.8.8.8"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn409WhenCouponExhausted() throws Exception {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            mockMvc.perform(post("/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"MAXONE","maxUsages":1,"country":"PL"}
                            """));

            mockMvc.perform(post("/coupons/MAXONE/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"userId":"first-user"}
                            """)
                    .header("X-Forwarded-For", "89.64.55.1"));

            mockMvc.perform(post("/coupons/MAXONE/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"second-user"}
                                    """)
                            .header("X-Forwarded-For", "89.64.55.1"))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn409WhenSameUserUsesAgain() throws Exception {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            mockMvc.perform(post("/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"ONEPERUSER","maxUsages":100,"country":"PL"}
                            """));

            mockMvc.perform(post("/coupons/ONEPERUSER/usages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"userId":"user-1"}
                            """)
                    .header("X-Forwarded-For", "89.64.55.1"));

            mockMvc.perform(post("/coupons/ONEPERUSER/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """)
                            .header("X-Forwarded-For", "89.64.55.1"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Coupon 'ONEPERUSER' has already been used by user 'user-1'"));
        }

        @Test
        void shouldAllowDifferentUsersToUseSameCoupon() throws Exception {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            mockMvc.perform(post("/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"SHARED","maxUsages":100,"country":"PL"}
                            """));

            mockMvc.perform(post("/coupons/SHARED/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """)
                            .header("X-Forwarded-For", "89.64.55.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentUsages").value(1));

            mockMvc.perform(post("/coupons/SHARED/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-2"}
                                    """)
                            .header("X-Forwarded-For", "89.64.55.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentUsages").value(2));
        }

        @Test
        void shouldTreatCouponCodeCaseInsensitively() throws Exception {
            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));

            mockMvc.perform(post("/coupons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code":"wiosna","maxUsages":10,"country":"PL"}
                            """));

            mockMvc.perform(post("/coupons/WIOSNA/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """)
                            .header("X-Forwarded-For", "89.64.55.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("WIOSNA"));
        }
    }
}
