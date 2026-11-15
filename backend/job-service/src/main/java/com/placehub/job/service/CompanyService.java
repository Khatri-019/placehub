package com.placehub.job.service;

import com.placehub.common.error.ApiException;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.paging.Paging;
import com.placehub.job.dto.CompanyRequest;
import com.placehub.job.dto.CompanyResponse;
import com.placehub.job.entity.Company;
import com.placehub.job.mapper.JobMapper;
import com.placehub.job.repository.CompanyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CompanyService {

    static final String ALREADY_EXISTS = "You already have a company profile";
    private static final Map<String, String> SORTABLE = Map.of("name", "name", "createdAt", "createdAt");

    private final CompanyRepository companies;

    public CompanyService(CompanyRepository companies) {
        this.companies = companies;
    }

    @Transactional
    public CompanyResponse create(UUID recruiterId, CompanyRequest req) {
        if (companies.existsByRecruiterOwnerUserId(recruiterId)) {
            throw ApiException.conflict(ALREADY_EXISTS);
        }
        Company company = new Company(UUID.randomUUID(), recruiterId);
        apply(company, req);
        try {
            companies.saveAndFlush(company);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(ALREADY_EXISTS);
        }
        return JobMapper.toResponse(company);
    }

    @Transactional(readOnly = true)
    public CompanyResponse mine(UUID recruiterId) {
        return JobMapper.toResponse(ownCompany(recruiterId));
    }

    @Transactional
    public CompanyResponse updateMine(UUID recruiterId, CompanyRequest req) {
        Company company = ownCompany(recruiterId);
        apply(company, req);
        companies.saveAndFlush(company);
        return JobMapper.toResponse(company);
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(UUID id) {
        return companies.findById(id).map(JobMapper::toResponse)
                .orElseThrow(() -> ApiException.notFound("Company not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> list(Integer page, Integer size, String sort) {
        return PageResponse.of(companies.findAll(Paging.of(page, size, sort, SORTABLE, Sort.by("name"))),
                JobMapper::toResponse);
    }

    Company ownCompany(UUID recruiterId) {
        return companies.findByRecruiterOwnerUserId(recruiterId)
                .orElseThrow(() -> ApiException.notFound("You have not created a company profile yet"));
    }

    private static void apply(Company company, CompanyRequest req) {
        company.update(req.name().trim(), blankToNull(req.website()), blankToNull(req.description()),
                blankToNull(req.industry()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
