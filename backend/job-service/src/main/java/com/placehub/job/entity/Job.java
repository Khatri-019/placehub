package com.placehub.job.entity;

import com.placehub.job.domain.Branch;
import com.placehub.job.domain.EmploymentType;
import com.placehub.job.domain.JobStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "recruiter_owner_user_id", nullable = false, length = 36)
    private UUID recruiterOwnerUserId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 150)
    private String location;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Column(length = 100)
    private String ctc;

    /** Numeric CTC in lakhs per annum; required to publish a FULL_TIME job (spec 20.1). */
    @Column(name = "ctc_lpa", precision = 6, scale = 2)
    private BigDecimal ctcLpa;

    @Column(name = "min_cgpa", nullable = false, precision = 4, scale = 2)
    private BigDecimal minCgpa;

    @Column(name = "max_backlogs", nullable = false)
    private int maxBacklogs;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "job_eligible_branches", joinColumns = @JoinColumn(name = "job_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "branch", nullable = false, length = 10)
    private Set<Branch> eligibleBranches = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "job_eligible_graduation_years", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "graduation_year", nullable = false)
    private Set<Integer> eligibleGraduationYears = new HashSet<>();

    @Column(nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
    }

    public Job(UUID id, Company company) {
        this.id = id;
        this.company = company;
        this.recruiterOwnerUserId = company.getRecruiterOwnerUserId();
        this.status = JobStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void applyDetails(String title, String description, String location, EmploymentType type, String ctc,
                             BigDecimal ctcLpa, BigDecimal minCgpa, int maxBacklogs, Set<Branch> branches, Set<Integer> years,
                             Instant deadline) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.employmentType = type;
        this.ctc = ctc;
        this.ctcLpa = ctcLpa;
        this.minCgpa = minCgpa;
        this.maxBacklogs = maxBacklogs;
        this.eligibleBranches.clear();
        if (branches != null && !branches.isEmpty()) {
            this.eligibleBranches.addAll(EnumSet.copyOf(branches));
        }
        this.eligibleGraduationYears.clear();
        if (years != null) {
            this.eligibleGraduationYears.addAll(years);
        }
        this.deadline = deadline;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public boolean isOwnedBy(UUID userId) {
        return recruiterOwnerUserId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public UUID getRecruiterOwnerUserId() {
        return recruiterOwnerUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public String getCtc() {
        return ctc;
    }

    public BigDecimal getCtcLpa() {
        return ctcLpa;
    }

    public BigDecimal getMinCgpa() {
        return minCgpa;
    }

    public int getMaxBacklogs() {
        return maxBacklogs;
    }

    public Set<Branch> getEligibleBranches() {
        return eligibleBranches;
    }

    public Set<Integer> getEligibleGraduationYears() {
        return eligibleGraduationYears;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
