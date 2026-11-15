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
    public record ApplyRequest(@NotNull UUID jobId, @jakarta.validation.Valid ConsentRequest consent) {
    }

    /** Consent for jobs in a REQUIRE_CONSENT band (spec 20.3). */
    public record ConsentRequest(Boolean accepted, @Size(max = 100) String signedName) {
    }

    public record StatusUpdateRequest(@NotNull ApplicationStatus status, @Size(max = 1000) String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApplicationResponse(UUID id, UUID jobId, UUID studentUserId, UUID recruiterOwnerUserId,
                                      String jobTitle, String companyName, String studentName, String studentEmail,
                                      String branch, BigDecimal cgpa, int graduationYear, int backlogCount,
                                      ApplicationStatus status, String recruiterNote, Instant appliedAt,
                                      Instant updatedAt, BigDecimal ctcLpa, String employmentType,
                                      boolean hasResume, String policyDecision, boolean autoShortlisted) {
    }

    /**
     * Apply preview. {@code eligible}/{@code reasons} keep their v2 meaning (Job Service criteria only); the v3 fields
     * describe every other requirement so the UI can show everything before Apply.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EligibilityPreview(UUID jobId, boolean eligible, List<String> reasons, boolean alreadyApplied,
                                     boolean resumeRequired, boolean hasResume, boolean canApply,
                                     List<Map<String, String>> violations, Object policy, Object consent,
                                     Object placementState) {

        public EligibilityPreview(UUID jobId, boolean eligible, List<String> reasons, boolean alreadyApplied) {
            this(jobId, eligible, reasons, alreadyApplied, false, false, eligible && !alreadyApplied, List.of(), null,
                    null, null);
        }
    }

    public record StatusStats(long total, Map<ApplicationStatus, Long> byStatus) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryEntry(ApplicationStatus fromStatus, ApplicationStatus toStatus, String actorType,
                               UUID actorUserId, String reason, Instant changedAt) {
    }

    public record BulkStatusRequest(@jakarta.validation.constraints.NotEmpty @Size(max = 100) List<@NotNull UUID> applicationIds,
                                    @NotNull ApplicationStatus status, @Size(max = 1000) String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BulkItem(UUID applicationId, boolean success, ApplicationStatus status, String code,
                           String message) {
    }

    public record BulkResult(List<BulkItem> results, long succeeded, long failed) {
    }

    public record AutoScreenResult(int shortlisted, int evaluated) {
    }
}
