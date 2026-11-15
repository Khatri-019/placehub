package com.placehub.job.controller;

import com.placehub.job.dto.EligibilityRequest;
import com.placehub.job.dto.EligibilityResponse;
import com.placehub.job.dto.JobSummaryResponse;
import com.placehub.job.service.JobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service endpoints (X-Internal-Token). Validation always answers 200 unless the job is missing. */
@RestController
@RequestMapping("/internal/jobs")
@Tag(name = "internal")
public class InternalJobController {

    private final JobService jobService;

    public InternalJobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{id}/summary")
    public JobSummaryResponse summary(@PathVariable UUID id) {
        return jobService.summary(id);
    }

    @PostMapping("/{id}/validate-eligibility")
    public EligibilityResponse validate(@PathVariable UUID id, @Valid @RequestBody EligibilityRequest request) {
        return jobService.validateEligibility(id, request);
    }
}
