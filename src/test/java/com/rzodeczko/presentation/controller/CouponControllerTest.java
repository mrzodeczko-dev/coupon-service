package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.input.CreateCouponUseCase;
import com.rzodeczko.application.port.input.UseCouponUseCase;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponController.class)
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCouponUseCase createCouponUseCase;

    @MockitoBean
    private UseCouponUseCase useCouponUseCase;

    @Nested
    class CreateCouponIT {

        @Test
        void shouldReturn201WithCouponData() throws Exception {
            var coupon = Coupon.create(new CouponCode("SUMMER25"), 100, new Country("PL"));
            given(createCouponUseCase.create(any())).willReturn(coupon);

            mockMvc.perform(post("/coupons")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"SUMMER25","maxUsages":100,"country":"PL"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(coupon.getId().toString()))
                    .andExpect(jsonPath("$.code").value("SUMMER25"))
                    .andExpect(jsonPath("$.maxUsages").value(100))
                    .andExpect(jsonPath("$.country").value("PL"));

            then(createCouponUseCase).should().create(argThat(cmd ->
                    cmd.code().equals("SUMMER25") && cmd.maxUsages() == 100 && cmd.country().equals("PL")
            ));
        }
    }

    @Nested
    class UseCouponIT {

        @Test
        void shouldReturn200WithUsageData() throws Exception {
            var coupon = Coupon.create(new CouponCode("SUMMER25"), 10, new Country("PL"));
            coupon.use(new Country("PL"));

            given(useCouponUseCase.use(any())).willReturn(coupon);

            mockMvc.perform(post("/coupons/SUMMER25/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """)
                            .with(request -> { request.setRemoteAddr("89.64.55.1"); return request; }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUMMER25"))
                    .andExpect(jsonPath("$.currentUsages").value(1))
                    .andExpect(jsonPath("$.maxUsages").value(10))
                    .andExpect(jsonPath("$.remainingUsages").value(9));

            then(useCouponUseCase).should().use(argThat(cmd ->
                    cmd.code().equals("SUMMER25") && cmd.userId().equals("user-1") && cmd.ipAddress().equals("89.64.55.1")
            ));
        }

        @Test
        void shouldUseRemoteAddr() throws Exception {
            var coupon = Coupon.create(new CouponCode("SUMMER25"), 10, new Country("PL"));
            coupon.use(new Country("PL"));

            given(useCouponUseCase.use(any())).willReturn(coupon);

            mockMvc.perform(post("/coupons/SUMMER25/usages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":"user-1"}
                                    """))
                    .andExpect(status().isOk());

            then(useCouponUseCase).should().use(argThat(cmd ->
                    cmd.ipAddress().equals("127.0.0.1")
            ));
        }
    }
}
