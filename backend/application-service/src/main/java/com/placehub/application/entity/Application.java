package com.placehub.application.entity;

import com.placehub.application.domain.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A student's application with snapshots taken at apply time. External IDs (job, student, recruiter) are plain UUIDs;
 * there are no foreign keys to other services. Job ownership cannot change in v1, so the stored
 * recruiterOwnerUserId is a safe basis for ownership checks.
 */
@Entity
@Table(name = "applications")
public class Application {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "student_user_id", nullable = false)
    private UUID studentUserId;

    @Column(name = "recruiter_owner_user_id", nullable = false)
    private UUID recruiterOwnerUserId;

    @Column(name = "job_title_snapshot", nullable = false, length = 150)
    private String jobTitleSnapshot;

    @Column(name = "company_name_snapshot", nullable = false, length = 150)
    private String companyNameSnapshot;

    @Column(name = "student_name_snapshot", nullable = false, length = 100)
    private String studentNameSnapshot;

    @Column(name = "student_email_snapshot", nullable = false, length = 254)
    private String studentEmailSnapshot;

    @Column(name = "branch_snapshot", nullable = false, length = 10)
    private String branchSnapshot;

    @Column(name = "cgpa_snapshot", nullable = false, precision = 4, scale = 2)
    private BigDecimal cgpaSnapshot;

    @Column(name = "graduation_year_snapshot", nullable = false)
    private int graduationYearSnapshot;

    @Column(name = "backlog_count_snapshot", nullable = false)
    private int backlogCountSnapshot;

    @Column(name = "ctc_lpa_snapshot", precision = 6, scale = 2)
    private BigDecimal ctcLpaSnapshot;

    @Column(name = "employment_type_snapshot", length = 20)
    private String employmentTypeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "recruiter_note", length = 1000)
    private String recruiterNote;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Application() {
    }

    public Application(UUID id, UUID jobId, UUID studentUserId, UUID recruiterOwnerUserId, String jobTitle,
                       String companyName, String studentName, String studentEmail, String branch, BigDecimal cgpa,
                       int graduationYear, int backlogCount) {
        this.id = id;
        this.jobId = jobId;
        this.studentUserId = studentUserId;
        this.recruiterOwnerUserId = recruiterOwnerUserId;
        this.jobTitleSnapshot = jobTitle;
        this.companyNameSnapshot = companyName;
        this.studentNameSnapshot = studentName;
        this.studentEmailSnapshot = studentEmail;
        this.branchSnapshot = branch;
        this.cgpaSnapshot = cgpa;
        this.graduationYearSnapshot = graduationYear;
        this.backlogCountSnapshot = backlogCount;
        this.status = ApplicationStatus.APPLIED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        appliedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Job compensation snapshot taken at apply time (spec 20.1); never changes afterwards. */
    public void snapshotJobTerms(BigDecimal ctcLpa, String employmentType) {
        this.ctcLpaSnapshot = ctcLpa;
        this.employmentTypeSnapshot = employmentType;
    }

    public void changeStatus(ApplicationStatus newStatus, String note) {
        this.status = newStatus;
        if (note != null) {
            this.recruiterNote = note.isBlank() ? null : note.trim();
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getStudentUserId() {
        return studentUserId;
    }

    public UUID getRecruiterOwnerUserId() {
        return recruiterOwnerUserId;
    }

    public String getJobTitleSnapshot() {
        return jobTitleSnapshot;
    }

    public String getCompanyNameSnapshot() {
        return companyNameSnapshot;
    }

    public String getStudentNameSnapshot() {
        return studentNameSnapshot;
    }

    public String getStudentEmailSnapshot() {
        return studentEmailSnapshot;
    }

    public String getBranchSnapshot() {
        return branchSnapshot;
    }

    public BigDecimal getCgpaSnapshot() {
        return cgpaSnapshot;
    }

    public int getGraduationYearSnapshot() {
        return graduationYearSnapshot;
    }

    public int getBacklogCountSnapshot() {
        return backlogCountSnapshot;
    }

    public BigDecimal getCtcLpaSnapshot() {
        return ctcLpaSnapshot;
    }

    public String getEmploymentTypeSnapshot() {
        return employmentTypeSnapshot;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getRecruiterNote() {
        return recruiterNote;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
