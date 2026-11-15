package com.placehub.application;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.placehub.application", "com.placehub.common"})
public class ApplicationServiceApplication {

    public static void main(String[] args) {
        // Hosts with legacy zone ids (e.g. Asia/Calcutta) are rejected by PostgreSQL 17; all timestamps are UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ApplicationServiceApplication.class, args);
    }
}
