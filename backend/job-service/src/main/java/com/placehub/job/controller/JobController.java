package com.placehub.job.controller;

import com.placehub.common.paging.PageResponse;
import com.placehub.common.security.AuthUser;
import com.placehub.job.domain.Branch;
import com.placehub.job.domain.EmploymentType;
import com.placehub.job.domain.JobStatus;
import com.placehub.job.dto.CompanyResponse;
import com.placehub.job.dto.JobRequest;
import com.placehub.job.dto.JobResponse;
import com.placehub.job.service.CompanyService;
import com.placehub.job.service.JobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@Tag(name = "jobs")
public class JobController {

    private final JobService jobService;
    private final CompanyService companyService;

    public JobController(JobService jobService, CompanyService companyService) {
        this.jobService = jobService;
        this.companyService = companyService;
    }

    @PostMapping("/api/jobs")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JobRequest request) {
        JobResponse created = jobService.create(AuthUser.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/jobs/" + created.id())).body(created);
    }

    @PutMapping("/api/jobs/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody JobRequest request) {
        return jobService.update(AuthUser.from(jwt), id, request);
    }

    @PatchMapping("/api/jobs/{id}/publish")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return jobService.publish(AuthUser.from(jwt), id);
    }

    @PatchMapping("/api/jobs/{id}/close")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public JobResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return jobService.close(AuthUser.from(jwt), id);
    }

    @GetMapping("/api/jobs")
    public PageResponse<JobResponse> browse(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String location,
                                            @RequestParam(required = false) EmploymentType employmentType,
                                            @RequestParam(required = false) Branch branch,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String sort) {
        return jobService.browse(q, location, employmentType, branch, page, size, sort);
    }

    @GetMapping("/api/jobs/{id}")
    public JobResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return jobService.get(AuthUser.from(jwt), id);
    }

    @GetMapping("/api/recruiter/jobs")
    @PreAuthorize("hasRole('RECRUITER')")
    public PageResponse<JobResponse> recruiterJobs(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) JobStatus status,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort) {
        return jobService.recruiterJobs(AuthUser.from(jwt), status, page, size, sort);
    }

    @GetMapping("/api/admin/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<JobResponse> adminJobs(@RequestParam(required = false) JobStatus status,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort) {
        return jobService.adminJobs(status, q, page, size, sort);
    }

    @GetMapping("/api/admin/companies")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CompanyResponse> adminCompanies(@RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size,
                                                        @RequestParam(required = false) String sort) {
        return companyService.list(page, size, sort);
    }
}
