package com.placehub.application.service;

import com.placehub.application.client.ClientDtos;
import com.placehub.application.client.DependencyGateway;
import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.dto.ApplicationDtos;
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

import com.placehub.application.policy.Consent;
import com.placehub.application.policy.ConsentText;
import com.placehub.application.policy.PlacementService;
import com.placehub.application.policy.PolicyDtos;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
    private final ApplyRequirements requirements;
    private final PlacementService placements;
    private final Clock clock;
    private final com.placehub.application.repository.StatusHistoryRepository historyRepository;

    public ApplicationService(ApplicationRepository applications, ApplicationWriter writer,
                              DependencyGateway dependencies, ApplyRequirements requirements,
                              PlacementService placements, Clock clock,
                              com.placehub.application.repository.StatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
        this.applications = applications;
        this.writer = writer;
        this.dependencies = dependencies;
        this.requirements = requirements;
        this.placements = placements;
        this.clock = clock;
    }

    /** Client details recorded with a consent (forwarded by the gateway). */
    public record ClientInfo(String ip, String userAgent) {
        public static final ClientInfo NONE = new ClientInfo(null, null);
    }

    // ------------------------------------------------------------------ student

    public ApplicationResponse apply(AuthUser student, UUID jobId) {
        return apply(student, jobId, null, ClientInfo.NONE);
    }

    /**
     * Apply flow (spec 20.3): eligibility, resume, placement policy and consent are all checked and every failure is
     * returned in one 422; then the duplicate check; then the application (+ consent) is persisted atomically.
     */
    public ApplicationResponse apply(AuthUser student, UUID jobId, ApplyRequirements.ConsentInput consent,
                                     ClientInfo client) {
        requireStudent(student);
        ClientDtos.AcademicProfile profile = activeProfile(student);
        ApplyRequirements.Assessment a = requirements.assess(student.id(), profile, jobId, consent);
        a.violations().throwIfAny();
        if (applications.existsByJobIdAndStudentUserId(jobId, student.id())) {
            throw ApiException.conflict(ALREADY_APPLIED);
        }
        ClientDtos.JobSummary job = a.job();
        Application application = new Application(UUID.randomUUID(), jobId, student.id(),
                job.recruiterOwnerUserId(), job.title(), job.companyName(), profile.fullName(), profile.email(),
                profile.branch(), profile.cgpa(), profile.graduationYear(), profile.backlogCount());
        application.snapshotJobTerms(job.ctcLpa(), job.employmentType());
        var ev = a.evaluation();
        application.snapshotPolicy(ev.policyVersion(), ev.decision().name(), ev.ratio(),
                ev.matchedBand() == null ? null : ev.matchedBand().label(), ev.lockOnPlacement());
        if (profile.currentResumeId() != null) {
            application.snapshotResume(profile.currentResumeId());
            dependencies.referenceResume(student.id(), profile.currentResumeId());
        }
        ApplicationWriter.ConsentFactory consentRecord = null;
        if (a.consentNeeded() && a.consentAccepted()) {
            String rendered = a.renderedConsent();
            String band = ev.matchedBand() == null ? "" : ev.matchedBand().label();
            String signed = consent.signedName().trim();
            consentRecord = appId -> new Consent(UUID.randomUUID(), appId, student.id(), jobId, ev.policyVersion(),
                    band, ConsentText.sha256(rendered), rendered, signed, clock.instant(),
                    truncate(client.ip(), 64), truncate(client.userAgent(), 512));
        }
        boolean auto = AutoShortlist.qualifies(job, profile.cgpa(), profile.backlogCount());
        try {
            return ApplicationMapper.toResponse(writer.create(application, consentRecord, auto), false);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(ALREADY_APPLIED);
        }
    }

    public EligibilityPreview preview(AuthUser student, UUID jobId) {
        requireStudent(student);
        ClientDtos.AcademicProfile profile = activeProfile(student);
        ApplyRequirements.Assessment a = requirements.assess(student.id(), profile, jobId, null);
        boolean alreadyApplied = applications.existsByJobIdAndStudentUserId(jobId, student.id());
        List<String> reasons = a.eligibility().reasons() == null ? List.of() : a.eligibility().reasons();
        List<Map<String, String>> codes = a.violations().list().stream()
                .map(v -> Map.of("code", v.code().name(), "message", v.message())).toList();
        // Consent is given at apply time, so a pending consent does not stop the student from proceeding.
        boolean blocking = a.violations().list().stream()
                .anyMatch(v -> v.code() != Violations.Code.CONSENT_REQUIRED);
        var view = placements.view(student.id());
        Map<String, Object> placementState = new LinkedHashMap<>();
        placementState.put("currentCtc", view.currentCtc());
        placementState.put("placed", view.placed());
        placementState.put("locked", view.locked());
        placementState.put("summary", view.summary());
        return new EligibilityPreview(jobId, a.eligibility().eligible(), reasons, alreadyApplied,
                a.rules().resumeRequired(), profile.currentResumeId() != null, !blocking && !alreadyApplied, codes,
                PolicyDtos.EvaluationDto.of(a.evaluation()), a.consentDetails(), placementState);
    }

    public PolicyDtos.PlacementStateView placementState(AuthUser student) {
        requireStudent(student);
        return placements.view(student.id());
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

    /**
     * Streams the resume version captured by the application. Same visibility as {@link #get}: owning student, owning
     * recruiter or admin; anyone else gets 404, so ids cannot be probed (spec 20.2 IDOR rule).
     */
    public org.springframework.http.ResponseEntity<byte[]> resume(AuthUser viewer, UUID applicationId) {
        Application application = applications.findById(applicationId).orElseThrow(ApplicationService::notFound);
        boolean isStudentOwner = viewer.isStudent() && application.getStudentUserId().equals(viewer.id());
        if (!isStudentOwner && !isRecruiterOwner(viewer, application) && !viewer.isAdmin()) {
            throw notFound();
        }
        if (application.getResumeIdSnapshot() == null) {
            throw ApiException.notFound("No resume was attached to this application");
        }
        var upstream = dependencies.resumeFile(application.getStudentUserId(), application.getResumeIdSnapshot());
        byte[] body = upstream.getBody() == null ? new byte[0] : upstream.getBody();
        String filename = com.placehub.common.web.PdfDownloads.filenameOf(upstream.getHeaders());
        return com.placehub.common.web.PdfDownloads.attachment(body, filename);
    }

    /** Status timeline; same visibility as {@link #get} (owning student, owning recruiter, admin; else 404). */
    public List<ApplicationDtos.HistoryEntry> history(AuthUser viewer, UUID applicationId) {
        Application application = applications.findById(applicationId).orElseThrow(ApplicationService::notFound);
        boolean isStudentOwner = viewer.isStudent() && application.getStudentUserId().equals(viewer.id());
        if (!isStudentOwner && !isRecruiterOwner(viewer, application) && !viewer.isAdmin()) {
            throw notFound();
        }
        return historyRepository.timeline(applicationId).stream()
                .map(h -> new ApplicationDtos.HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getActorType(),
                        isStudentOwner && !"SYSTEM".equals(h.getActorType()) && !viewer.id().equals(h.getActorUserId())
                                ? null : h.getActorUserId(),
                        isStudentOwner && h.getToStatus() != ApplicationStatus.WITHDRAWN && !"SYSTEM".equals(h.getActorType())
                                ? null : h.getReason(),
                        h.getChangedAt()))
                .toList();
    }

    /** Each item runs in its own transaction with the single-update rules; failures never affect other items. */
    public ApplicationDtos.BulkResult bulkStatus(AuthUser actor, List<UUID> ids, ApplicationStatus status, String note) {
        List<ApplicationDtos.BulkItem> results = new java.util.ArrayList<>();
        for (UUID id : new java.util.LinkedHashSet<>(ids)) {
            try {
                ApplicationResponse updated = updateStatus(actor, id, status, note);
                results.add(new ApplicationDtos.BulkItem(id, true, updated.status(), null, null));
            } catch (ApiException e) {
                // Applications outside the caller's jobs are reported as not found, never as forbidden.
                boolean hidden = e.getStatus() == HttpStatus.FORBIDDEN || e.getStatus() == HttpStatus.NOT_FOUND;
                results.add(new ApplicationDtos.BulkItem(id, false, null, hidden ? "NOT_FOUND" : e.getStatus().name(),
                        hidden ? "Application not found" : e.getMessage()));
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                results.add(new ApplicationDtos.BulkItem(id, false, null, "CONFLICT",
                        "The application was modified concurrently"));
            }
        }
        long ok = results.stream().filter(ApplicationDtos.BulkItem::success).count();
        return new ApplicationDtos.BulkResult(results, ok, results.size() - ok);
    }

    /** Shortlists every qualifying APPLIED application of an AUTO_SHORTLIST job as SYSTEM (spec 20.4). */
    public ApplicationDtos.AutoScreenResult autoScreen(AuthUser actor, UUID jobId) {
        ClientDtos.JobSummary job = dependencies.jobSummary(jobId);
        if (!actor.isAdmin() && !(actor.isRecruiter() && actor.id().equals(job.recruiterOwnerUserId()))) {
            throw ApiException.forbidden("Only the job's recruiter or an admin can screen its applicants");
        }
        if (!"AUTO_SHORTLIST".equals(job.screeningMode())) {
            throw ApiException.conflict("Switch the job to automatic shortlisting first");
        }
        Specification<Application> spec = eq("jobId", jobId).and(statusIs(ApplicationStatus.APPLIED));
        List<Application> applied = applications.findAll(spec);
        int shortlisted = 0;
        for (Application a : applied) {
            if (AutoShortlist.qualifies(job, a.getCgpaSnapshot(), a.getBacklogCountSnapshot())) {
                writer.systemShortlist(a);
                shortlisted++;
            }
        }
        return new ApplicationDtos.AutoScreenResult(shortlisted, applied.size());
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

    private static String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
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
