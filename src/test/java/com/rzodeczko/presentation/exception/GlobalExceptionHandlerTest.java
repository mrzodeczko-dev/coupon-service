package com.rzodeczko.presentation.exception;

import com.rzodeczko.application.exception.GeoLocationException;
import com.rzodeczko.application.exception.GeoLocationNetworkException;
import com.rzodeczko.application.exception.GeoLocationUnavailableException;
import com.rzodeczko.application.port.input.CreateCouponUseCase;
import com.rzodeczko.application.port.input.UseCouponUseCase;
import com.rzodeczko.domain.exception.CouponAlreadyExistsException;
import com.rzodeczko.domain.exception.CouponAlreadyUsedByUserException;
import com.rzodeczko.domain.exception.CouponConcurrentModificationException;
import com.rzodeczko.domain.exception.CouponCountryMismatchException;
import com.rzodeczko.domain.exception.CouponExhaustedException;
import com.rzodeczko.domain.exception.CouponNotFoundException;
import com.rzodeczko.presentation.controller.CouponController;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CouponController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCouponUseCase createCouponUseCase;

    @MockitoBean
    private UseCouponUseCase useCouponUseCase;

    @Nested
    class ValidationErrors {

        @Test
        void shouldReturn400WhenRequestBodyInvalid() throws Exception {
            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"","maxUsages":0,"country":""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void shouldReturn400WhenCountryCodeInvalid() throws Exception {
            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"SUMMER26","maxUsages":10,"country":"INVALID"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void shouldReturn400WhenMaxUsagesExceedsUpperLimit() throws Exception {
            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"SUMMER26","maxUsages":1000001,"country":"PL"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("maxUsages: Max usages must not exceed 1000000"));
        }

        @Test
        void shouldReturn400WhenCodeExceedsMaxSize() throws Exception {
            String longCode = "A".repeat(101);
            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"%s","maxUsages":10,"country":"PL"}
                                    """.formatted(longCode)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("code: Coupon code must be at most 100 characters"));
        }
    }

    @Nested
    class DomainErrors {

        @Test
        void shouldReturn404WhenCouponNotFound() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponNotFoundException("UNKNOWN"));

            mockMvc.perform(post("/v1/coupons/UNKNOWN/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.detail").value("Coupon with code 'UNKNOWN' not found"));
        }

        @Test
        void shouldReturn409WhenCouponAlreadyExists() throws Exception {
            given(createCouponUseCase.create(any()))
                    .willThrow(new CouponAlreadyExistsException("SUMMER26"));

            mockMvc.perform(post("/v1/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"SUMMER26","maxUsages":10,"country":"PL"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Coupon with code 'SUMMER26' already exists"));
        }

        @Test
        void shouldReturn409WhenCouponExhausted() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponExhaustedException("SUMMER26"));

            mockMvc.perform(post("/v1/coupons/SUMMER26/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void shouldReturn409WhenCouponAlreadyUsedByUser() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponAlreadyUsedByUserException("SUMMER26", "user-1"));

            mockMvc.perform(post("/v1/coupons/SUMMER26/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Coupon 'SUMMER26' has already been used by user 'user-1'"));
        }

        @Test
        void shouldReturn409WhenCouponConcurrentlyModified() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponConcurrentModificationException("SUMMER26"));

            mockMvc.perform(post("/v1/coupons/SUMMER26/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Coupon 'SUMMER26' was modified concurrently, please retry"));
        }

        @Test
        void shouldReturn403WhenCountryMismatch() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponCountryMismatchException("PL", "US"));

            mockMvc.perform(post("/v1/coupons/SUMMER26/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    @Nested
    class InfrastructureErrors {

        @Test
        void shouldReturn503WhenGeoLocationServiceUnavailable() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new GeoLocationUnavailableException("Circuit breaker is open"));

            mockMvc.perform(post("/v1/coupons/SUMMER26/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.detail").value("Geolocation service is temporarily unavailable"));
        }

        @Test
        void shouldReturn503WhenGeoLocationNetworkFails() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new GeoLocationNetworkException("Connection timeout", new RuntimeException()));

            mockMvc.perform(post("/v1/coupons/SUMMER26/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.detail").value("Geolocation service is temporarily unavailable"));
        }
    }
}
