package com.placehub.application.policy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.placehub.application.policy.PolicyModel.Action;
import com.placehub.application.policy.PolicyModel.Band;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response DTOs for the placement policy APIs (spec 20.3). */
public final class PolicyDtos {

    private PolicyDtos() {
    }

    public record BandDto(@NotNull BigDecimal fromMultiplier, BigDecimal toMultiplier, @NotNull Action action,
                          boolean lockOnPlacement) {

        public Band toBand() {
            return new Band(fromMultiplier, toMultiplier, action, lockOnPlacement);
        }

        public static BandDto of(Band b) {
            return new BandDto(b.fromMultiplier(), b.toMultiplier(), b.action(), b.lockOnPlacement());
        }
    }

    /** Body of PUT /api/admin/policy: the full policy plus a required change note. */
    public record PolicyRequest(@NotEmpty @Size(max = 20) List<@Valid @NotNull BandDto> bands,
                                boolean appliesToInternships,
                                Integer maxActiveConsents,
                                Boolean resumeRequired,
                                Boolean withdrawOtherApplicationsOnLock,
                                @Size(max = 20_000) String consentText,
                                @NotBlank @Size(max = 500) String changeNote) {
    }

    /** A draft policy for the simulator (change note not required). */
    public record PolicyDraft(@NotEmpty @Size(max = 20) List<@Valid @NotNull BandDto> bands,
                              boolean appliesToInternships, Integer maxActiveConsents, Boolean resumeRequired,
                              Boolean withdrawOtherApplicationsOnLock, @Size(max = 20_000) String consentText) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PolicyResponse(UUID id, int version, boolean active, List<BandDto> bands,
                                 boolean appliesToInternships, Integer maxActiveConsents, boolean resumeRequired,
                                 boolean withdrawOtherApplicationsOnLock, String consentText, Instant effectiveFrom,
                                 UUID changedByUserId, String changeNote, Instant createdAt) {
    }

    public record SimulateRequest(@DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2)
                                  BigDecimal currentCtc,
                                  @DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2)
                                  BigDecimal jobCtc,
                                  @NotNull String employmentType,
                                  boolean locked,
                                  @Valid PolicyDraft policy) {
    }

    public record ReasonDto(String code, String message) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvaluationDto(String decision, List<ReasonDto> reasons, BandDto matchedBand, String matchedBandLabel,
                                boolean lockOnPlacement, int policyVersion, BigDecimal ratio) {

        public static EvaluationDto of(PolicyModel.Evaluation e) {
            return new EvaluationDto(e.decision().name(),
                    e.reasons().stream().map(r -> new ReasonDto(r.code().name(), r.message())).toList(),
                    e.matchedBand() == null ? null : BandDto.of(e.matchedBand()),
                    e.matchedBand() == null ? null : e.matchedBand().label(),
                    e.lockOnPlacement(), e.policyVersion(), e.ratio());
        }
    }

    public record LockRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record OffCampusRequest(@NotNull UUID studentUserId, @NotBlank @Size(max = 150) String companyName,
                                   @NotNull @DecimalMin("0.01") @DecimalMax("9999.99")
                                   @Digits(integer = 4, fraction = 2) BigDecimal ctcLpa,
                                   boolean lock) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlacementDto(UUID id, UUID studentUserId, String studentName, UUID applicationId, String companyName,
                               BigDecimal ctcLpa, String band, boolean locked, String source, Instant recordedAt,
                               UUID recordedBy, boolean studentCurrentlyLocked) {
    }

    public record StudentLockDto(UUID studentUserId, boolean locked, String lockReason, Instant lockedAt,
                                 int withdrawnApplications) {
    }

    /** Admin-only consent view (includes client IP and user agent; never exposed to recruiters). */
    public record ConsentDto(UUID id, UUID applicationId, UUID studentUserId, UUID jobId, int policyVersion,
                             String band, String consentTextHash, String renderedText, String signedName,
                             Instant acceptedAt, String clientIp, String userAgent) {
    }

    public record AuditDto(UUID id, UUID actorUserId, String action, UUID studentUserId, String before, String after,
                           String reason, Instant createdAt) {
    }

    public record BandExplanation(BigDecimal fromCtc, BigDecimal toCtc, String action, boolean lockOnPlacement,
                                  String description) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlacementStateView(BigDecimal currentCtc, boolean placed, boolean locked, String lockReason,
                                     List<PlacementDto> placements, int policyVersion, Instant policyEffectiveFrom,
                                     boolean appliesToInternships, List<BandExplanation> bands, String summary) {
    }
}
