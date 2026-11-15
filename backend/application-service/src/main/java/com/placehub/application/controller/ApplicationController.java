package com.placehub.application.controller;

import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.dto.ApplicationDtos.ApplicationResponse;
import com.placehub.application.dto.ApplicationDtos.ApplyRequest;
import com.placehub.application.dto.ApplicationDtos.EligibilityPreview;
import com.placehub.application.dto.ApplicationDtos.StatusStats;
import com.placehub.application.dto.ApplicationDtos.StatusUpdateRequest;
import com.placehub.application.service.ApplicationService;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.security.AuthUser;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@Tag(name = "applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/applications")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApplicationResponse> apply(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody ApplyRequest request) {
        ApplicationResponse created = service.apply(AuthUser.from(jwt), request.jobId());
        return ResponseEntity.created(URI.create("/api/applications/" + created.id())).body(created);
    }

    @GetMapping("/api/applications/eligibility")
    @PreAuthorize("hasRole('STUDENT')")
    public EligibilityPreview eligibility(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID jobId) {
        return service.preview(AuthUser.from(jwt), jobId);
    }

    @GetMapping("/api/applications/me")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<ApplicationResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(required = false) ApplicationStatus status,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String sort) {
        return service.mine(AuthUser.from(jwt), status, page, size, sort);
    }

    @GetMapping("/api/applications/stats/me")
    @PreAuthorize("hasRole('STUDENT')")
    public StatusStats myStats(@AuthenticationPrincipal Jwt jwt) {
        return service.studentStats(AuthUser.from(jwt));
    }

    @GetMapping("/api/applications/stats/recruiter")
    @PreAuthorize("hasRole('RECRUITER')")
    public StatusStats recruiterStats(@AuthenticationPrincipal Jwt jwt) {
        return service.recruiterStats(AuthUser.from(jwt));
    }

    @GetMapping("/api/applications/job/{jobId}")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public PageResponse<ApplicationResponse> forJob(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID jobId,
                                                    @RequestParam(required = false) ApplicationStatus status,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size,
                                                    @RequestParam(required = false) String sort) {
        return service.forJob(AuthUser.from(jwt), jobId, status, page, size, sort);
    }

    @GetMapping("/api/applications/{id}")
    public ApplicationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(AuthUser.from(jwt), id);
    }

    @PatchMapping("/api/applications/{id}/withdraw")
    @PreAuthorize("hasRole('STUDENT')")
    public ApplicationResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.withdraw(AuthUser.from(jwt), id);
    }

    @PatchMapping("/api/applications/{id}/status")
    @PreAuthorize("hasAnyRole('RECRUITER', 'ADMIN')")
    public ApplicationResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(AuthUser.from(jwt), id, request.status(), request.note());
    }

    @GetMapping("/api/admin/applications")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ApplicationResponse> adminList(@RequestParam(required = false) ApplicationStatus status,
                                                       @RequestParam(required = false) UUID jobId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size,
                                                       @RequestParam(required = false) String sort) {
        return service.adminList(status, jobId, page, size, sort);
    }
}
