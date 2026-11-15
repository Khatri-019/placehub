package com.placehub.application.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/** Resolved through Eureka as lb://user-service; timeouts in spring.cloud.openfeign.client.config.user-service. */
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/internal/users/{id}/academic-profile")
    ClientDtos.AcademicProfile academicProfile(@PathVariable("id") UUID id);
}
