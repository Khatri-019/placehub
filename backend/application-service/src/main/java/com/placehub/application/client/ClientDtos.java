package com.placehub.application.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** This service's own copies of the internal contracts it consumes (never shared jars). */
public final class ClientDtos {

    private ClientDtos() {
    }

    /** User Service GET /internal/users/{id}/academic-profile. */
    public record AcademicProfile(UUID userId, String fullName, String email, String branch, int graduationYear,
                                  BigDecimal cgpa, int backlogCount, String status, UUID currentResumeId) {

        /** Constructor for callers that predate v3 (no resume). */
        public AcademicProfile(UUID userId, String fullName, String email, String branch, int graduationYear,
                               BigDecimal cgpa, int backlogCount, String status) {
            this(userId, fullName, email, branch, graduationYear, cgpa, backlogCount, status, null);
        }
    }

    /** Job Service POST /internal/jobs/{id}/validate-eligibility request. */
    public record EligibilityCheck(String branch, int graduationYear, BigDecimal cgpa, int backlogCount) {
    }

    /** Job Service POST /internal/jobs/{id}/validate-eligibility response (always 200 for an existing job). */
    public record EligibilityResult(UUID jobId, boolean eligible, List<String> reasons) {
    }

    /** Job Service GET /internal/jobs/{id}/summary. */
    public record JobSummary(UUID jobId, String title, UUID companyId, String companyName, UUID recruiterOwnerUserId,
                             String status, Instant deadline, BigDecimal ctcLpa, String employmentType) {

        /** Compact constructor for callers that predate the v3 fields. */
        public JobSummary(UUID jobId, String title, UUID companyId, String companyName, UUID recruiterOwnerUserId,
                          String status, Instant deadline) {
            this(jobId, title, companyId, companyName, recruiterOwnerUserId, status, deadline, null, null);
        }

        public boolean isFullTime() {
            return "FULL_TIME".equals(employmentType);
        }
    }
}
