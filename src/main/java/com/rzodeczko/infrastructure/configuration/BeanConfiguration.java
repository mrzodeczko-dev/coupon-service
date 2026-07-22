package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.application.service.CouponService;
import com.rzodeczko.domain.repository.CouponRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @Bean
    public CouponService couponService(CouponRepository couponRepository) {
        return new CouponService(couponRepository);
    }
}
