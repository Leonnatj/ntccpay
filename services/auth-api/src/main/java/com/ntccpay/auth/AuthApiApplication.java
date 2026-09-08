package com.ntccpay.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // drives the transactional-outbox relay (Phase 3)
public class AuthApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApiApplication.class, args);
    }
}
