package com.placehub.application.service;

import com.placehub.application.client.ClientDtos;
import com.placehub.application.client.DependencyGateway;
import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.dto.ApplicationDtos.ApplicationResponse;
import com.placehub.application.dto.ApplicationDtos.EligibilityPreview;
import com.placehub.application.dto.ApplicationDtos.StatusStats;
import com.placehub.application.entity.Application;
import com.placehub.application.mapper.ApplicationMapper;
import com.placehub.application.repository.ApplicationRepository;
import com.placehub.common.error.ApiException;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.paging.Paging;
import com.placehub.common.security.AuthUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application workflows (spec section 5.3). Remote calls happen outside any DB transaction; persistence is
 * delegated to {@link ApplicationWriter} so each change and its outbox row commit atomically.
 */
@Service
public class ApplicationService {

    static final String ALREADY_APPLIED = "You have already applied to this job";
    static final String NOT_ELIGIBLE = "You are not eligible for this job";
    static final String INACTIVE = "Only active student accounts can apply";
    private static final Map<String, String> SORTABLE = Map.of(
            "appliedAt", "appliedAt", "updatedAt", "updatedAt", "status", "status",
            "studentName", "studentNameSnapshot", "cgpa", "cgpaSnapshot", "jobTitle", "jobTitleSnapshot");
    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "appliedAt");

    private final ApplicationRepository applications;
    private final ApplicationWriter writer;
    private final DependencyGateway dependencies;

    public ApplicationService(ApplicationRepository applications, ApplicationWriter writer,
                              DependencyGateway dependencies) {
        this.applications = applications;
        this.writer = writer;
        this.dependencies = dependencies;
    }

    // ------------------------------------------------------------------ student

    // UNPROCESSABLE_ENTITY (deprecated alias in Spring 7) keeps the reason phrase used by the spec's error example.
    @SuppressWarnings("deprecation")
    public ApplicationResponse apply(AuthUser student, UUID jobId) {
        requireStudent(student);
        ClientDtos.AcademicProfile profile = activeProfile(student);
        ClientDtos.EligibilityResult result = dependencies.validateEligibility(jobId, toCheck(profile));
        if (!result.eligible()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, NOT_ELIGIBLE,
                    Map.of("reasons", result.reasons() == null ? List.of() : result.reasons()));
        }
        ClientDtos.JobSummary job = dependencies.jobSummary(jobId);
        Application application = new Application(UUID.randomUUID(), jobId, student.id(),
                job.recruiterOwnerUserId(), job.title(), job.companyName(), profile.fullName(), profile.email(),
                profile.branch(), profile.cgpa(), profile.graduationYear(), profile.backlogCount());
        application.snapshotJobTerms(job.ctcLpa(), job.employmentType());
        try {
            return ApplicationMapper.toResponse(writer.create(application), false);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(ALREADY_APPLIED);
        }
    }

    public EligibilityPreview preview(AuthUser student, UUID jobId) {
        requireStudent(student);
        ClientDtos.AcademicProfile profile = activeProfile(student);
        ClientDtos.EligibilityResult result = dependencies.validateEligibility(jobId, toCheck(profile));
        boolean alreadyApplied = applications.existsByJobIdAndStudentUserId(jobId, student.id());
        return new EligibilityPreview(jobId, result.eligible(),
                result.reasons() == null ? List.of() : result.reasons(), alreadyApplied);
    }

    public PageResponse<ApplicationResponse> mine(AuthUser student, ApplicationStatus status, Integer page,
                                                  Integer size, String sort) {
        requireStudent(student);
        Specification<Application> spec = eq("studentUserId", student.id()).and(statusIs(status));
        return PageResponse.of(applications.findAll(spec, Paging.of(page, size, sort, SORTABLE, NEWEST)),
                a -> ApplicationMapper.toResponse(a, false));
    }

    public ApplicationResponse withdraw(AuthUser student, UUID applicationId) {
        requireStudent(student);
        Application application = applications.findById(applicationId)
                .filter(a -> a.getStudentUserId().equals(student.id()))
                .orElseThrow(ApplicationService::notFound);
        requireTransition(application.getStatus(), ApplicationStatus.WITHDRAWN);
        return ApplicationMapper.toResponse(
                writer.changeStatus(application, ApplicationStatus.WITHDRAWN, null, student.id()), false);
    }

    public StatusStats studentStats(AuthUser student) {
        requireStudent(student);
        return stats(applications.countByStatusForStudent(student.id()));
    }

    // ------------------------------------------------------------------ shared

    /** Owning student, owning recruiter or admin; everyone else gets 404 to avoid id probing. */
    public ApplicationResponse get(AuthUser viewer, UUID applicationId) {
        Application application = applications.findById(applicationId).orElseThrow(ApplicationService::notFound);
        boolean isStudentOwner = viewer.isStudent() && application.getStudentUserId().equals(viewer.id());
        boolean isRecruiterOwner = isRecruiterOwner(viewer, application);
        if (!isStudentOwner && !isRecruiterOwner && !viewer.isAdmin()) {
            throw notFound();
        }
        return ApplicationMapper.toResponse(application, !isStudentOwner);
    }

    // ------------------------------------------------------------------ recruiter / admin

    public PageResponse<ApplicationResponse> forJob(AuthUser viewer, UUID jobId, ApplicationStatus status,
                                                    Integer page, Integer size, String sort) {
        if (!viewer.isAdmin()) {
            if (!viewer.isRecruiter()) {
                throw ApiException.forbidden("Only the job's recruiter can view its applicants");
            }
            ClientDtos.JobSummary job = dependencies.jobSummary(jobId);
            if (!viewer.id().equals(job.recruiterOwnerUserId())) {
                throw ApiException.forbidden("Only the job's recruiter can view its applicants");
            }
        }
        Specification<Application> spec = eq("jobId", jobId).and(statusIs(status));
        return PageResponse.of(applications.findAll(spec, Paging.of(page, size, sort, SORTABLE, NEWEST)),
                a -> ApplicationMapper.toResponse(a, true));
    }

    public ApplicationResponse updateStatus(AuthUser actor, UUID applicationId, ApplicationStatus newStatus,
                                            String note) {
        Application application = applications.findById(applicationId).orElseThrow(ApplicationService::notFound);
        if (!actor.isAdmin() && !isRecruiterOwner(actor, application)) {
            throw ApiException.forbidden("Only the job's recruiter or an admin can update this application");
        }
        if (newStatus == ApplicationStatus.WITHDRAWN) {
            throw ApiException.badRequest("Only the student can withdraw an application");
        }
        requireTransition(application.getStatus(), newStatus);
        return ApplicationMapper.toResponse(writer.changeStatus(application, newStatus, note, actor.id()), true);
    }

    public PageResponse<ApplicationResponse> adminList(ApplicationStatus status, UUID jobId, Integer page,
                                                       Integer size, String sort) {
        Specification<Application> spec = statusIs(status);
        if (jobId != null) {
            spec = spec.and(eq("jobId", jobId));
        }
        return PageResponse.of(applications.findAll(spec, Paging.of(page, size, sort, SORTABLE, NEWEST)),
                a -> ApplicationMapper.toResponse(a, true));
    }

    public StatusStats recruiterStats(AuthUser recruiter) {
        if (!recruiter.isRecruiter()) {
            throw ApiException.forbidden("Recruiters only");
        }
        return stats(applications.countByStatusForRecruiter(recruiter.id()));
    }

    // ------------------------------------------------------------------ helpers

    private ClientDtos.AcademicProfile activeProfile(AuthUser student) {
        ClientDtos.AcademicProfile profile = dependencies.academicProfile(student.id());
        if (!"ACTIVE".equals(profile.status())) {
            throw ApiException.forbidden(INACTIVE);
        }
        return profile;
    }

    private static ClientDtos.EligibilityCheck toCheck(ClientDtos.AcademicProfile p) {
        return new ClientDtos.EligibilityCheck(p.branch(), p.graduationYear(), p.cgpa(), p.backlogCount());
    }

    static void requireTransition(ApplicationStatus from, ApplicationStatus to) {
        if (from == to) {
            throw ApiException.conflict("Application is already " + to);
        }
        if (!from.canTransitionTo(to)) {
            throw ApiException.conflict("Cannot move from " + from + " to " + to);
        }
    }

    private static void requireStudent(AuthUser user) {
        if (!user.isStudent()) {
            throw ApiException.forbidden("Students only");
        }
    }

    private static boolean isRecruiterOwner(AuthUser user, Application application) {
        return user.isRecruiter() && application.getRecruiterOwnerUserId().equals(user.id());
    }

    private static ApiException notFound() {
        return ApiException.notFound("Application not found");
    }

    private static Specification<Application> eq(String attribute, Object value) {
        return (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    private static Specification<Application> statusIs(ApplicationStatus status) {
        return status == null ? (root, query, cb) -> cb.conjunction() : eq("status", status);
    }

    static StatusStats stats(List<Object[]> rows) {
        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            byStatus.put(s, 0L);
        }
        long total = 0;
        for (Object[] row : rows) {
            long count = ((Number) row[1]).longValue();
            byStatus.put((ApplicationStatus) row[0], count);
            total += count;
        }
        return new StatusStats(total, byStatus);
    }
}
