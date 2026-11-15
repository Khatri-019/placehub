package com.placehub.job.service;

import com.placehub.common.error.ApiException;
import com.placehub.common.security.AuthUser;
import com.placehub.job.domain.Branch;
import com.placehub.job.domain.EmploymentType;
import com.placehub.job.domain.JobStatus;
import com.placehub.job.dto.EligibilityRequest;
import com.placehub.job.dto.JobRequest;
import com.placehub.job.entity.Company;
import com.placehub.job.entity.Job;
import com.placehub.job.repository.CompanyRepository;
import com.placehub.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

    static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    final UUID ownerId = UUID.randomUUID();
    final AuthUser owner = new AuthUser(ownerId, AuthUser.RECRUITER, "o@x.com", "Owner");
    final AuthUser otherRecruiter = new AuthUser(UUID.randomUUID(), AuthUser.RECRUITER, "r@x.com", "Other");
    final AuthUser student = new AuthUser(UUID.randomUUID(), AuthUser.STUDENT, "s@x.com", "Student");
    final AuthUser admin = new AuthUser(UUID.randomUUID(), AuthUser.ADMIN, "a@x.com", "Admin");

    JobRepository jobs = mock(JobRepository.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    JobService service = new JobService(jobs, companies, new EligibilityEvaluator(clock), clock);
    Company company;
    Job job;

    @BeforeEach
    void setUp() {
        company = new Company(UUID.randomUUID(), ownerId);
        company.update("Northstar", null, null, null);
        job = new Job(UUID.randomUUID(), company);
        job.applyDetails("SE", "desc", "Pune", EmploymentType.FULL_TIME, null, new BigDecimal("7.00"), 0,
                Set.of(Branch.CSE), Set.of(), NOW.plusSeconds(86400));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
    }

    JobRequest request(Instant deadline) {
        return new JobRequest("SE II", "desc", "Pune", EmploymentType.FULL_TIME, "10 LPA", null, null,
                Set.of(Branch.CSE, Branch.ISE), Set.of(2027), deadline);
    }

    static HttpStatus status(Throwable t) {
        return ((ApiException) t).getStatus();
    }

    @Test
    void createWithoutCompanyIs409() {
        when(companies.findByRecruiterOwnerUserId(ownerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(owner, request(NOW.plusSeconds(60))))
                .isInstanceOf(ApiException.class).hasMessage(JobService.COMPANY_FIRST)
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void newJobsAreDraftAndDefaultCriteria() {
        when(companies.findByRecruiterOwnerUserId(ownerId)).thenReturn(Optional.of(company));
        var res = service.create(owner, request(NOW.plusSeconds(60)));
        assertThat(res.status()).isEqualTo(JobStatus.DRAFT);
        assertThat(res.minCgpa()).isEqualByComparingTo("0.00");
        assertThat(res.maxBacklogs()).isZero();
        assertThat(res.recruiterOwnerUserId()).isEqualTo(ownerId);
        assertThat(res.eligibleBranches()).containsExactly(Branch.CSE, Branch.ISE);
    }

    @Test
    void onlyOwnerCanUpdateOrPublish() {
        assertThatThrownBy(() -> service.update(otherRecruiter, job.getId(), request(NOW.plusSeconds(60))))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.publish(otherRecruiter, job.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.update(admin, job.getId(), request(NOW.plusSeconds(60))))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        verify(jobs, never()).saveAndFlush(any());
    }

    @Test
    void closeAllowedForOwnerAndAdminOnly() {
        assertThatThrownBy(() -> service.close(otherRecruiter, job.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service.close(admin, job.getId()).status()).isEqualTo(JobStatus.CLOSED);
        assertThatThrownBy(() -> service.close(owner, job.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void publishRequiresFutureDeadlineAndDraft() {
        job.applyDetails("SE", "d", "P", EmploymentType.FULL_TIME, null, BigDecimal.ZERO, 0, Set.of(), Set.of(), NOW);
        assertThatThrownBy(() -> service.publish(owner, job.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        job.applyDetails("SE", "d", "P", EmploymentType.FULL_TIME, null, BigDecimal.ZERO, 0, Set.of(), Set.of(),
                NOW.plusSeconds(1));
        assertThat(service.publish(owner, job.getId()).status()).isEqualTo(JobStatus.PUBLISHED);
        assertThatThrownBy(() -> service.publish(owner, job.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void closedJobsAreReadOnly() {
        job.setStatus(JobStatus.CLOSED);
        assertThatThrownBy(() -> service.update(owner, job.getId(), request(NOW.plusSeconds(60))))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void draftsAreHiddenFromStudentsAndOtherRecruiters() {
        assertThatThrownBy(() -> service.get(student, job.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.get(otherRecruiter, job.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.get(owner, job.getId()).id()).isEqualTo(job.getId());
        assertThat(service.get(admin, job.getId()).id()).isEqualTo(job.getId());
        job.setStatus(JobStatus.PUBLISHED);
        assertThat(service.get(student, job.getId()).status()).isEqualTo(JobStatus.PUBLISHED);
    }

    @Test
    void closedJobsAreHiddenFromStudents() {
        job.setStatus(JobStatus.CLOSED);
        assertThatThrownBy(() -> service.get(student, job.getId()))
                .satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void eligibilityAlwaysReturnsResultForExistingJob() {
        var res = service.validateEligibility(job.getId(), new EligibilityRequest(Branch.ME, 2027,
                new BigDecimal("6.0"), 0));
        assertThat(res.eligible()).isFalse();
        assertThat(res.reasons()).hasSize(3);
        UUID missing = UUID.randomUUID();
        when(jobs.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.validateEligibility(missing, new EligibilityRequest(Branch.ME, 2027,
                BigDecimal.TEN, 0))).satisfies(e -> assertThat(status(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(JobService.likePattern(" 100%_Done ")).isEqualTo("%100\\%\\_done%");
    }
}
