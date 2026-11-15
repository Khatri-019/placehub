package com.placehub.application.service;

import com.placehub.application.client.ClientDtos;
import com.placehub.application.client.DependencyGateway;
import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.entity.Application;
import com.placehub.application.repository.ApplicationRepository;
import com.placehub.common.error.ApiException;
import com.placehub.common.error.DependencyUnavailableException;
import com.placehub.common.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationServiceTest {

    final UUID jobId = UUID.randomUUID();
    final UUID recruiterId = UUID.randomUUID();
    final AuthUser student = new AuthUser(UUID.randomUUID(), AuthUser.STUDENT, "s@x.com", "Asha");
    final AuthUser otherStudent = new AuthUser(UUID.randomUUID(), AuthUser.STUDENT, "o@x.com", "Other");
    final AuthUser recruiter = new AuthUser(recruiterId, AuthUser.RECRUITER, "r@x.com", "Riya");
    final AuthUser otherRecruiter = new AuthUser(UUID.randomUUID(), AuthUser.RECRUITER, "r2@x.com", "Ravi");
    final AuthUser admin = new AuthUser(UUID.randomUUID(), AuthUser.ADMIN, "a@x.com", "Admin");

    ApplicationRepository repo = mock(ApplicationRepository.class);
    ApplicationWriter writer = mock(ApplicationWriter.class);
    DependencyGateway deps = mock(DependencyGateway.class);
    ApplicationService service = new ApplicationService(repo, writer, deps);

    final UUID resumeId = UUID.randomUUID();

    ClientDtos.AcademicProfile profile(String status) {
        return new ClientDtos.AcademicProfile(student.id(), "Asha", "s@x.com", "CSE", 2027, new BigDecimal("8.60"),
                0, status, resumeId);
    }

    Application existing(ApplicationStatus status) {
        Application a = new Application(UUID.randomUUID(), jobId, student.id(), recruiterId, "SE", "Northstar",
                "Asha", "s@x.com", "CSE", new BigDecimal("8.60"), 2027, 0);
        if (status != ApplicationStatus.APPLIED) {
            a.changeStatus(status, null);
        }
        when(repo.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    @BeforeEach
    void setUp() {
        when(deps.academicProfile(student.id())).thenReturn(profile("ACTIVE"));
        when(deps.jobSummary(jobId)).thenReturn(new ClientDtos.JobSummary(jobId, "Graduate SE", UUID.randomUUID(),
                "Northstar", recruiterId, "PUBLISHED", Instant.now().plusSeconds(3600), new BigDecimal("12.00"),
                "FULL_TIME"));
        when(writer.create(any())).thenAnswer(inv -> inv.getArgument(0));
        when(writer.changeStatus(any(), any(), any(), any())).thenAnswer(inv -> {
            Application a = inv.getArgument(0);
            a.changeStatus(inv.getArgument(1), inv.getArgument(2));
            return a;
        });
    }

    static HttpStatus status(Throwable t) {
        return ((ApiException) t).getStatus();
    }

    @Test
    void eligibleStudentAppliesWithTrustedSnapshots() {
        when(deps.validateEligibility(eq(jobId), any())).thenReturn(
                new ClientDtos.EligibilityResult(jobId, true, List.of()));
        var res = service.apply(student, jobId);
        assertThat(res.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(res.recruiterOwnerUserId()).isEqualTo(recruiterId);
        assertThat(res.jobTitle()).isEqualTo("Graduate SE");
        assertThat(res.companyName()).isEqualTo("Northstar");
        assertThat(res.cgpa()).isEqualByComparingTo("8.60");
        assertThat(res.studentUserId()).isEqualTo(student.id());
        assertThat(res.ctcLpa()).isEqualByComparingTo("12.00");
        assertThat(res.employmentType()).isEqualTo("FULL_TIME");
        verify(writer).create(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ineligibleStudentGets422WithReasonsAndNothingIsCreated() {
        when(deps.validateEligibility(eq(jobId), any())).thenReturn(new ClientDtos.EligibilityResult(jobId, false,
                List.of("Minimum CGPA required is 7.50; yours is 6.90")));
        assertThatThrownBy(() -> service.apply(student, jobId))
                .isInstanceOf(ApiException.class)
                .hasMessage(ApplicationService.NOT_ELIGIBLE)
                .satisfies(e -> {
                    assertThat(status(e)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    Map<String, Object> details = ((ApiException) e).getDetails();
                    assertThat((List<String>) details.get("reasons"))
                            .containsExactly("Minimum CGPA required is 7.50; yours is 6.90");
                });
        verify(writer, never()).create(any());
    }

    @Test
    void resumeIsSnapshotAndPinnedAtApplyTime() {
        when(deps.validateEligibility(eq(jobId), any())).thenReturn(
                new ClientDtos.EligibilityResult(jobId, true, List.of()));
        var res = service.apply(student, jobId);
        assertThat(res.hasResume()).isTrue();
        verify(deps).referenceResume(student.id(), resumeId);
        org.mockito.ArgumentCaptor<Application> saved = org.mockito.ArgumentCaptor.forClass(Application.class);
        verify(writer).create(saved.capture());
        assertThat(saved.getValue().getResumeIdSnapshot()).isEqualTo(resumeId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingResumeIs422ResumeRequiredTogetherWithEligibilityReasons() {
        when(deps.academicProfile(student.id())).thenReturn(new ClientDtos.AcademicProfile(student.id(), "Asha",
                "s@x.com", "CSE", 2027, new BigDecimal("8.60"), 0, "ACTIVE", null));
        when(deps.validateEligibility(eq(jobId), any())).thenReturn(new ClientDtos.EligibilityResult(jobId, false,
                List.of("Minimum CGPA required is 9.00; yours is 8.60")));
        assertThatThrownBy(() -> service.apply(student, jobId))
                .satisfies(e -> {
                    assertThat(status(e)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    Map<String, Object> d = ((ApiException) e).getDetails();
                    assertThat((List<String>) d.get("reasons")).containsExactly(
                            "Minimum CGPA required is 9.00; yours is 8.60", "Upload your resume before applying");
                    assertThat((List<Map<String, String>>) d.get("violations")).extracting(m -> m.get("code"))
                            .containsExactly("NOT_ELIGIBLE", "RESUME_REQUIRED");
                });
        verify(writer, never()).create(any());
        verify(deps, never()).referenceResume(any(), any());
    }

    @Test
    void resumeDownloadIsDeniedWith404ToAnyoneOutsideTheApplication() {
        Application a = existing(ApplicationStatus.APPLIED);
        a.snapshotResume(resumeId);
        // IDOR: another recruiter, another student and an unrelated recruiter id must never get the file.
        for (AuthUser outsider : List.of(otherRecruiter, otherStudent,
                new AuthUser(UUID.randomUUID(), AuthUser.RECRUITER, "x@x.com", "X"))) {
            assertThatThrownBy(() -> service.resume(outsider, a.getId()))
                    .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        }
        verify(deps, never()).resumeFile(any(), any());
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename("cv.pdf").build());
        when(deps.resumeFile(student.id(), resumeId)).thenReturn(
                new org.springframework.http.ResponseEntity<>("%PDF-1.4".getBytes(), h, HttpStatus.OK));
        for (AuthUser allowed : List.of(student, recruiter, admin)) {
            var res = service.resume(allowed, a.getId());
            assertThat(res.getHeaders().getContentType()).hasToString("application/pdf");
            assertThat(res.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(res.getHeaders().getContentDisposition().getFilename()).isEqualTo("cv.pdf");
        }
        verify(deps, org.mockito.Mockito.times(3)).resumeFile(student.id(), resumeId);
    }

    @Test
    void resumeDownloadWithoutSnapshotIs404() {
        Application a = existing(ApplicationStatus.APPLIED);
        assertThatThrownBy(() -> service.resume(student, a.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void duplicateIsMappedFromTheUniqueConstraintTo409() {
        when(deps.validateEligibility(eq(jobId), any())).thenReturn(
                new ClientDtos.EligibilityResult(jobId, true, List.of()));
        when(writer.create(any())).thenThrow(new DataIntegrityViolationException("uq_app_job_student"));
        assertThatThrownBy(() -> service.apply(student, jobId))
                .hasMessage(ApplicationService.ALREADY_APPLIED)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void dependencyDownGives503AndCreatesNothing() {
        when(deps.validateEligibility(eq(jobId), any()))
                .thenThrow(new DependencyUnavailableException(DependencyGateway.UNAVAILABLE));
        assertThatThrownBy(() -> service.apply(student, jobId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        when(deps.academicProfile(student.id()))
                .thenThrow(new DependencyUnavailableException(DependencyGateway.UNAVAILABLE));
        assertThatThrownBy(() -> service.apply(student, jobId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(writer, never()).create(any());
    }

    @Test
    void inactiveStudentGets403() {
        when(deps.academicProfile(student.id())).thenReturn(profile("DEACTIVATED"));
        assertThatThrownBy(() -> service.apply(student, jobId))
                .hasMessage(ApplicationService.INACTIVE)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        verify(deps, never()).validateEligibility(any(), any());
    }

    @Test
    void nonStudentsCannotApply() {
        assertThatThrownBy(() -> service.apply(recruiter, jobId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void previewReportsAlreadyApplied() {
        when(deps.validateEligibility(eq(jobId), any())).thenReturn(
                new ClientDtos.EligibilityResult(jobId, true, List.of()));
        when(repo.existsByJobIdAndStudentUserId(jobId, student.id())).thenReturn(true);
        var preview = service.preview(student, jobId);
        assertThat(preview.eligible()).isTrue();
        assertThat(preview.alreadyApplied()).isTrue();
    }

    @Test
    void statusUpdateRequiresOwningRecruiterOrAdmin() {
        Application a = existing(ApplicationStatus.APPLIED);
        assertThatThrownBy(() -> service.updateStatus(otherRecruiter, a.getId(), ApplicationStatus.SHORTLISTED, null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.updateStatus(student, a.getId(), ApplicationStatus.SHORTLISTED, null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        var res = service.updateStatus(recruiter, a.getId(), ApplicationStatus.SHORTLISTED, "Strong profile");
        assertThat(res.status()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(res.recruiterNote()).isEqualTo("Strong profile");
        assertThat(service.updateStatus(admin, a.getId(), ApplicationStatus.SELECTED, null).status())
                .isEqualTo(ApplicationStatus.SELECTED);
        verify(writer).changeStatus(any(), eq(ApplicationStatus.SHORTLISTED), eq("Strong profile"), eq(recruiterId));
    }

    @Test
    void statusUpdateValidatesTransitions() {
        Application a = existing(ApplicationStatus.APPLIED);
        assertThatThrownBy(() -> service.updateStatus(recruiter, a.getId(), ApplicationStatus.WITHDRAWN, null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateStatus(recruiter, a.getId(), ApplicationStatus.APPLIED, null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
        Application rejected = existing(ApplicationStatus.REJECTED);
        assertThatThrownBy(() -> service.updateStatus(recruiter, rejected.getId(), ApplicationStatus.SHORTLISTED,
                null)).hasMessage("Cannot move from REJECTED to SHORTLISTED")
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
        UUID missing = UUID.randomUUID();
        when(repo.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateStatus(admin, missing, ApplicationStatus.SHORTLISTED, null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void withdrawRules() {
        Application a = existing(ApplicationStatus.SHORTLISTED);
        assertThatThrownBy(() -> service.withdraw(otherStudent, a.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.withdraw(recruiter, a.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service.withdraw(student, a.getId()).status()).isEqualTo(ApplicationStatus.WITHDRAWN);
        verify(writer).changeStatus(any(), eq(ApplicationStatus.WITHDRAWN), any(), eq(student.id()));
        Application selected = existing(ApplicationStatus.SELECTED);
        assertThatThrownBy(() -> service.withdraw(student, selected.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void getHidesApplicationsFromNonParticipantsWith404() {
        Application a = existing(ApplicationStatus.APPLIED);
        assertThatThrownBy(() -> service.get(otherStudent, a.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.get(otherRecruiter, a.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.get(student, a.getId()).id()).isEqualTo(a.getId());
        assertThat(service.get(recruiter, a.getId()).id()).isEqualTo(a.getId());
        assertThat(service.get(admin, a.getId()).id()).isEqualTo(a.getId());
    }

    @Test
    void applicantsListRequiresJobOwnership() {
        assertThatThrownBy(() -> service.forJob(otherRecruiter, jobId, null, 0, 20, null))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void statsAreZeroFilled() {
        var stats = ApplicationService.stats(List.<Object[]>of(new Object[]{ApplicationStatus.APPLIED, 2L},
                new Object[]{ApplicationStatus.SHORTLISTED, 1L}));
        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.byStatus()).hasSize(ApplicationStatus.values().length)
                .containsEntry(ApplicationStatus.SELECTED, 0L).containsEntry(ApplicationStatus.APPLIED, 2L);
    }
}
