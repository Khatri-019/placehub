package com.placehub.application.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/** Resolved through Eureka as lb://user-service; timeouts in spring.cloud.openfeign.client.config.user-service. */
@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/internal/users/{id}/academic-profile")
    ClientDtos.AcademicProfile academicProfile(@PathVariable("id") UUID id);

    @PostMapping("/internal/users/{userId}/resumes/{resumeId}/reference")
    void referenceResume(@PathVariable("userId") UUID userId, @PathVariable("resumeId") UUID resumeId);

    @GetMapping("/internal/users/{userId}/resumes/{resumeId}/file")
    ResponseEntity<byte[]> resumeFile(@PathVariable("userId") UUID userId, @PathVariable("resumeId") UUID resumeId);
}
