package com.placehub.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.placehub.application.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response DTOs for the public API. */
public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    /** Only the job id is accepted; identity and academic data never come from the client. */
    public record ApplyRequest(@NotNull UUID jobId) {
    }

    public record StatusUpdateRequest(@NotNull ApplicationStatus status, @Size(max = 1000) String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApplicationResponse(UUID id, UUID jobId, UUID studentUserId, UUID recruiterOwnerUserId,
                                      String jobTitle, String companyName, String studentName, String studentEmail,
                                      String branch, BigDecimal cgpa, int graduationYear, int backlogCount,
                                      ApplicationStatus status, String recruiterNote, Instant appliedAt,
                                      Instant updatedAt, BigDecimal ctcLpa, String employmentType) {
    }

    public record EligibilityPreview(UUID jobId, boolean eligible, List<String> reasons, boolean alreadyApplied) {
    }

    public record StatusStats(long total, Map<ApplicationStatus, Long> byStatus) {
    }
}
