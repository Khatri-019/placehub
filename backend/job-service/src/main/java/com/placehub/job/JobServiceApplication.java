package com.placehub.job;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.placehub.job", "com.placehub.common"})
public class JobServiceApplication {

    public static void main(String[] args) {
        // Hosts with legacy zone ids (e.g. Asia/Calcutta) are rejected by PostgreSQL 17; all timestamps are UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(JobServiceApplication.class, args);
    }
}
