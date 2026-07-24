package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.application.service.CouponService;
import com.rzodeczko.domain.repository.CouponRepository;
import com.rzodeczko.domain.repository.CouponUsageRepository;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class BeanConfiguration {

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2000))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(5000));

        return builder -> builder.requestFactory(requestFactory);
    }

    @Bean
    public CouponService couponService(CouponRepository couponRepository,
                                       CouponUsageRepository couponUsageRepository) {
        return new CouponService(couponRepository, couponUsageRepository);
    }
}
