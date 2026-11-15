package com.placehub.user;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.placehub.user", "com.placehub.common"})
@ConfigurationPropertiesScan
public class UserServiceApplication {

    public static void main(String[] args) {
        // Hosts with legacy zone ids (e.g. Asia/Calcutta) are rejected by PostgreSQL 17; all timestamps are UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
