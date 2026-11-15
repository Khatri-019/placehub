package com.placehub.discovery;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        // Hosts with legacy zone ids (e.g. Asia/Calcutta) are rejected by PostgreSQL 17; all timestamps are UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
