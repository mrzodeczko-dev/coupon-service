package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.application.service.CouponService;
import com.rzodeczko.domain.repository.CouponRepository;
import com.rzodeczko.domain.repository.CouponUsageRepository;
import com.rzodeczko.infrastructure.geolocation.adapter.FindIpGeoLocationAdapter;
import com.rzodeczko.infrastructure.geolocation.adapter.IpApiGeoLocationAdapter;
import com.rzodeczko.infrastructure.geolocation.resilience.ResilientGeoLocationAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(GeoLocationProperties.class)
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
    public GeoLocationProvider rawGeoLocationProvider(
            GeoLocationProperties properties,
            RestClient.Builder restClientBuilder) {
        return switch (properties.provider()) {
            case IP_API -> new IpApiGeoLocationAdapter(restClientBuilder);
            case FINDIP -> new FindIpGeoLocationAdapter(
                    restClientBuilder,
                    properties.findip().requireToken()
            );
        };
    }

    @Bean
    @Primary
    public GeoLocationProvider geoLocationProvider(GeoLocationProvider rawGeoLocationProvider) {
        return new ResilientGeoLocationAdapter(rawGeoLocationProvider);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CouponService couponService(CouponRepository couponRepository,
                                       CouponUsageRepository couponUsageRepository,
                                       Clock clock) {
        return new CouponService(couponRepository, couponUsageRepository, clock);
    }
}
