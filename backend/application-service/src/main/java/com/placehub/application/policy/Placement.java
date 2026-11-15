package com.placehub.application.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A recorded placement: an on-campus SELECTED FULL_TIME application, or an admin-recorded off-campus offer. */
@Entity
@Table(name = "placements")
public class Placement {

    public enum Source { ON_CAMPUS, OFF_CAMPUS }

    @Id
    private UUID id;

    @Column(name = "student_user_id", nullable = false)
    private UUID studentUserId;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "ctc_lpa", precision = 6, scale = 2)
    private BigDecimal ctcLpa;

    @Column(length = 120)
    private String band;

    @Column(nullable = false)
    private boolean locked;

    @Column(nullable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Source source;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    protected Placement() {
    }

    public Placement(UUID id, UUID studentUserId, String studentName, UUID applicationId, String companyName,
                     BigDecimal ctcLpa, String band, boolean locked, Source source, Instant recordedAt,
                     UUID recordedBy) {
        this.id = id;
        this.studentUserId = studentUserId;
        this.studentName = studentName;
        this.applicationId = applicationId;
        this.companyName = companyName;
        this.ctcLpa = ctcLpa;
        this.band = band;
        this.locked = locked;
        this.source = source;
        this.recordedAt = recordedAt;
        this.recordedBy = recordedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentUserId() {
        return studentUserId;
    }

    public String getStudentName() {
        return studentName;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public BigDecimal getCtcLpa() {
        return ctcLpa;
    }

    public String getBand() {
        return band;
    }

    public boolean isLocked() {
        return locked;
    }

    public Source getSource() {
        return source;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }
}
