package com.placehub.job.controller;

import com.placehub.common.security.AuthUser;
import com.placehub.job.dto.CompanyRequest;
import com.placehub.job.dto.CompanyResponse;
import com.placehub.job.service.CompanyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
@Tag(name = "companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<CompanyResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CompanyRequest request) {
        CompanyResponse created = companyService.create(AuthUser.from(jwt).id(), request);
        return ResponseEntity.created(URI.create("/api/companies/" + created.id())).body(created);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('RECRUITER')")
    public CompanyResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return companyService.mine(AuthUser.from(jwt).id());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('RECRUITER')")
    public CompanyResponse updateMine(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyRequest request) {
        return companyService.updateMine(AuthUser.from(jwt).id(), request);
    }

    @GetMapping("/{id}")
    public CompanyResponse get(@PathVariable UUID id) {
        return companyService.get(id);
    }
}
