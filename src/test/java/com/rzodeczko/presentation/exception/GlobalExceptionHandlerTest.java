package com.rzodeczko.presentation.exception;

import com.rzodeczko.application.exception.GeoLocationException;
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
            mockMvc.perform(post("/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"","maxUsages":0,"country":""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void shouldReturn400WhenCountryCodeInvalid() throws Exception {
            mockMvc.perform(post("/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"SUMMER25","maxUsages":10,"country":"INVALID"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    class DomainErrors {

        @Test
        void shouldReturn404WhenCouponNotFound() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponNotFoundException("UNKNOWN"));

            mockMvc.perform(post("/coupons/UNKNOWN/usages")
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
                    .willThrow(new CouponAlreadyExistsException("SUMMER25"));

            mockMvc.perform(post("/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"SUMMER25","maxUsages":10,"country":"PL"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Coupon with code 'SUMMER25' already exists"));
        }

        @Test
        void shouldReturn409WhenCouponExhausted() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponExhaustedException("SUMMER25"));

            mockMvc.perform(post("/coupons/SUMMER25/usages")
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
                    .willThrow(new CouponAlreadyUsedByUserException("SUMMER25", "user-1"));

            mockMvc.perform(post("/coupons/SUMMER25/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Coupon 'SUMMER25' has already been used by user 'user-1'"));
        }

        @Test
        void shouldReturn409WhenCouponConcurrentlyModified() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponConcurrentModificationException("SUMMER25"));

            mockMvc.perform(post("/coupons/SUMMER25/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Coupon 'SUMMER25' was modified concurrently, please retry"));
        }

        @Test
        void shouldReturn403WhenCountryMismatch() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new CouponCountryMismatchException("PL", "US"));

            mockMvc.perform(post("/coupons/SUMMER25/usages")
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
        void shouldReturn503WhenGeoLocationFails() throws Exception {
            given(useCouponUseCase.use(any()))
                    .willThrow(new GeoLocationException("Connection timeout"));

            mockMvc.perform(post("/coupons/SUMMER25/usages")
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
