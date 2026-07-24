package com.rzodeczko;

import org.springframework.boot.SpringApplication;

/*dev-run*/
public class TestCouponServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(CouponServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run("--spring.profiles.active=it");
    }

}
