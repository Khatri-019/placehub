package com.placehub.application.controller;

import com.placehub.application.policy.ConsentService;
import com.placehub.application.policy.Placement;
import com.placehub.application.policy.PlacementService;
import com.placehub.application.policy.PolicyDtos.AuditDto;
import com.placehub.application.policy.PolicyDtos.ConsentDto;
import com.placehub.application.policy.PolicyDtos.EvaluationDto;
import com.placehub.application.policy.PolicyDtos.LockRequest;
import com.placehub.application.policy.PolicyDtos.OffCampusRequest;
import com.placehub.application.policy.PolicyDtos.PlacementDto;
import com.placehub.application.policy.PolicyDtos.PolicyRequest;
import com.placehub.application.policy.PolicyDtos.PolicyResponse;
import com.placehub.application.policy.PolicyDtos.SimulateRequest;
import com.placehub.application.policy.PolicyDtos.StudentLockDto;
import com.placehub.application.policy.PolicyService;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Placement office (ADMIN-only) endpoints for the placement policy engine (spec 20.3). */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "placement-policy")
public class AdminPolicyController {

    private final PolicyService policies;
    private final PlacementService placements;
    private final ConsentService consents;

    public AdminPolicyController(PolicyService policies, PlacementService placements, ConsentService consents) {
        this.policies = policies;
        this.placements = placements;
        this.consents = consents;
    }

    @GetMapping("/policy")
    public PolicyResponse active() {
        return policies.active();
    }

    @GetMapping("/policy/versions")
    public PageResponse<PolicyResponse> versions(@RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return policies.history(page, size);
    }

    @PutMapping("/policy")
    public PolicyResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PolicyRequest request) {
        return policies.update(AuthUser.from(jwt).id(), request);
    }

    @PostMapping("/policy/simulate")
    public EvaluationDto simulate(@Valid @RequestBody SimulateRequest request) {
        return policies.simulate(request);
    }

    @GetMapping("/policy/audit")
    public PageResponse<AuditDto> audit(@RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        return policies.audit(page, size);
    }

    @GetMapping("/placements")
    public PageResponse<PlacementDto> placements(@RequestParam(required = false) Boolean locked,
                                                 @RequestParam(required = false) Placement.Source source,
                                                 @RequestParam(required = false) String company,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) String sort) {
        return placements.list(locked, source, company, page, size, sort);
    }

    @PostMapping("/placements/off-campus")
    public ResponseEntity<PlacementDto> offCampus(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody OffCampusRequest request) {
        PlacementDto created = placements.recordOffCampus(AuthUser.from(jwt).id(), request);
        return ResponseEntity.created(URI.create("/api/admin/placements")).body(created);
    }

    @PostMapping("/students/{id}/lock")
    public StudentLockDto lock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody LockRequest request) {
        return placements.lock(AuthUser.from(jwt).id(), id, request.reason());
    }

    @PostMapping("/students/{id}/unlock")
    public StudentLockDto unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody LockRequest request) {
        return placements.unlock(AuthUser.from(jwt).id(), id, request.reason());
    }

    @GetMapping("/consents")
    public PageResponse<ConsentDto> consents(@RequestParam(required = false) UUID jobId,
                                             @RequestParam(required = false) UUID studentUserId,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String sort) {
        return consents.list(jobId, studentUserId, page, size, sort);
    }

    @GetMapping("/consents/export.csv")
    public ResponseEntity<byte[]> exportConsents(@RequestParam(required = false) UUID jobId,
                                                 @RequestParam(required = false) UUID studentUserId) {
        byte[] body = consents.exportCsv(jobId, studentUserId).getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("consents.csv").build());
        headers.setCacheControl(CacheControl.noStore().cachePrivate());
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
