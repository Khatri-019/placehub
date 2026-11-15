package com.placehub.application.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/** Resolved through Eureka as lb://job-service; timeouts in spring.cloud.openfeign.client.config.job-service. */
@FeignClient(name = "job-service")
public interface JobClient {

    @PostMapping("/internal/jobs/{id}/validate-eligibility")
    ClientDtos.EligibilityResult validateEligibility(@PathVariable("id") UUID jobId,
                                                    @RequestBody ClientDtos.EligibilityCheck request);

    @GetMapping("/internal/jobs/{id}/summary")
    ClientDtos.JobSummary summary(@PathVariable("id") UUID jobId);
}
