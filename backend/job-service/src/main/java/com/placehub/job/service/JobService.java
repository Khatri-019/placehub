package com.placehub.job.service;

import com.placehub.common.error.ApiException;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.paging.Paging;
import com.placehub.common.security.AuthUser;
import com.placehub.job.domain.Branch;
import com.placehub.job.domain.EmploymentType;
import com.placehub.job.domain.JobStatus;
import com.placehub.job.dto.EligibilityRequest;
import com.placehub.job.dto.EligibilityResponse;
import com.placehub.job.dto.JobRequest;
import com.placehub.job.dto.JobResponse;
import com.placehub.job.dto.JobSummaryResponse;
import com.placehub.job.entity.Company;
import com.placehub.job.entity.Job;
import com.placehub.job.mapper.JobMapper;
import com.placehub.job.repository.CompanyRepository;
import com.placehub.job.repository.JobRepository;
import jakarta.persistence.criteria.Join;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Job lifecycle, ownership and visibility rules (spec section 5.2). */
@Service
public class JobService {

    static final String COMPANY_FIRST = "Create your company profile first";
    static final String NOT_OWNER = "You can only manage your own jobs";
    private static final Map<String, String> BROWSE_SORT = Map.of("deadline", "deadline", "createdAt", "createdAt");
    private static final Map<String, String> MANAGE_SORT = Map.of(
            "deadline", "deadline", "createdAt", "createdAt", "title", "title", "status", "status");
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final EligibilityEvaluator evaluator;
    private final Clock clock;

    public JobService(JobRepository jobs, CompanyRepository companies, EligibilityEvaluator evaluator, Clock clock) {
        this.jobs = jobs;
        this.companies = companies;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ recruiter commands

    @Transactional
    public JobResponse create(AuthUser recruiter, JobRequest req) {
        Company company = companies.findByRecruiterOwnerUserId(recruiter.id())
                .orElseThrow(() -> ApiException.conflict(COMPANY_FIRST));
        Job job = new Job(UUID.randomUUID(), company);
        applyRequest(job, req);
        jobs.saveAndFlush(job);
        return JobMapper.toResponse(job);
    }

    @Transactional
    public JobResponse update(AuthUser recruiter, UUID jobId, JobRequest req) {
        Job job = load(jobId);
        requireOwner(recruiter, job);
        if (job.getStatus() == JobStatus.CLOSED) {
            throw ApiException.conflict("Closed jobs are read-only");
        }
        if (job.getStatus() == JobStatus.PUBLISHED && !req.deadline().isAfter(clock.instant())) {
            throw ApiException.badRequest("A published job's deadline must be in the future");
        }
        applyRequest(job, req);
        jobs.saveAndFlush(job);
        return JobMapper.toResponse(job);
    }

    @Transactional
    public JobResponse publish(AuthUser recruiter, UUID jobId) {
        Job job = load(jobId);
        requireOwner(recruiter, job);
        if (job.getStatus() != JobStatus.DRAFT) {
            throw ApiException.conflict("Only draft jobs can be published (current status: " + job.getStatus() + ")");
        }
        if (!job.getDeadline().isAfter(clock.instant())) {
            throw ApiException.badRequest("The deadline must be in the future to publish");
        }
        job.setStatus(JobStatus.PUBLISHED);
        jobs.saveAndFlush(job);
        return JobMapper.toResponse(job);
    }

    @Transactional
    public JobResponse close(AuthUser actor, UUID jobId) {
        Job job = load(jobId);
        if (!actor.isAdmin()) {
            requireOwner(actor, job);
        }
        if (job.getStatus() == JobStatus.CLOSED) {
            throw ApiException.conflict("Job is already closed");
        }
        job.setStatus(JobStatus.CLOSED);
        jobs.saveAndFlush(job);
        return JobMapper.toResponse(job);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public JobResponse get(AuthUser viewer, UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(JobService::jobNotFound);
        boolean visible = job.getStatus() == JobStatus.PUBLISHED || viewer.isAdmin()
                || (viewer.isRecruiter() && job.isOwnedBy(viewer.id()));
        if (!visible) {
            throw jobNotFound();
        }
        return JobMapper.toResponse(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> browse(String q, String location, EmploymentType type, Branch branch,
                                            Integer page, Integer size, String sort) {
        Specification<Job> spec = hasStatus(JobStatus.PUBLISHED).and(matches(q));
        if (location != null && !location.isBlank()) {
            String like = likePattern(location);
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("location")), like, '\\'));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("employmentType"), type));
        }
        if (branch != null) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.isEmpty(root.get("eligibleBranches")),
                    cb.isMember(branch, root.get("eligibleBranches"))));
        }
        return PageResponse.of(jobs.findAll(spec, Paging.of(page, size, sort, BROWSE_SORT, NEWEST)),
                JobMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> recruiterJobs(AuthUser recruiter, JobStatus status, Integer page, Integer size,
                                                   String sort) {
        Specification<Job> spec = (root, query, cb) -> cb.equal(root.get("recruiterOwnerUserId"), recruiter.id());
        if (status != null) {
            spec = spec.and(hasStatus(status));
        }
        return PageResponse.of(jobs.findAll(spec, Paging.of(page, size, sort, MANAGE_SORT, NEWEST)),
                JobMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> adminJobs(JobStatus status, String q, Integer page, Integer size, String sort) {
        Specification<Job> spec = matches(q);
        if (status != null) {
            spec = spec.and(hasStatus(status));
        }
        return PageResponse.of(jobs.findAll(spec, Paging.of(page, size, sort, MANAGE_SORT, NEWEST)),
                JobMapper::toResponse);
    }

    // ------------------------------------------------------------------ internal

    @Transactional(readOnly = true)
    public JobSummaryResponse summary(UUID jobId) {
        return JobMapper.toSummary(load(jobId));
    }

    @Transactional(readOnly = true)
    public EligibilityResponse validateEligibility(UUID jobId, EligibilityRequest req) {
        Job job = load(jobId);
        List<String> reasons = evaluator.evaluate(
                new EligibilityEvaluator.Criteria(job.getStatus(), job.getDeadline(), job.getMinCgpa(),
                        job.getMaxBacklogs(), job.getEligibleBranches(), job.getEligibleGraduationYears()),
                new EligibilityEvaluator.Candidate(req.branch(), req.graduationYear(), req.cgpa(),
                        req.backlogCount()));
        return new EligibilityResponse(jobId, reasons.isEmpty(), reasons);
    }

    // ------------------------------------------------------------------ helpers

    private Job load(UUID jobId) {
        return jobs.findById(jobId).orElseThrow(JobService::jobNotFound);
    }

    static void requireOwner(AuthUser user, Job job) {
        if (!user.isRecruiter() || !job.isOwnedBy(user.id())) {
            throw ApiException.forbidden(NOT_OWNER);
        }
    }

    private static ApiException jobNotFound() {
        return ApiException.notFound("Job not found");
    }

    private static void applyRequest(Job job, JobRequest req) {
        BigDecimal minCgpa = req.minCgpa() == null ? BigDecimal.ZERO : req.minCgpa();
        job.applyDetails(req.title().trim(), req.description().trim(), req.location().trim(), req.employmentType(),
                req.ctc() == null || req.ctc().isBlank() ? null : req.ctc().trim(),
                minCgpa.setScale(2, RoundingMode.HALF_UP),
                req.maxBacklogs() == null ? 0 : req.maxBacklogs(),
                req.eligibleBranches(), req.eligibleGraduationYears(), req.deadline());
    }

    private static Specification<Job> hasStatus(JobStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Job> matches(String q) {
        if (q == null || q.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String like = likePattern(q);
        return (root, query, cb) -> {
            Join<Job, Company> company = root.join("company");
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like, '\\'),
                    cb.like(cb.lower(root.get("description")), like, '\\'),
                    cb.like(cb.lower(company.get("name")), like, '\\'));
        };
    }

    static String likePattern(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
