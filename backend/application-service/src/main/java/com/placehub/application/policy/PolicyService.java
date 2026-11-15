package com.placehub.application.policy;

import com.placehub.application.policy.PolicyDtos.AuditDto;
import com.placehub.application.policy.PolicyDtos.BandDto;
import com.placehub.application.policy.PolicyDtos.EvaluationDto;
import com.placehub.application.policy.PolicyDtos.PolicyDraft;
import com.placehub.application.policy.PolicyDtos.PolicyRequest;
import com.placehub.application.policy.PolicyDtos.PolicyResponse;
import com.placehub.application.policy.PolicyDtos.SimulateRequest;
import com.placehub.application.policy.PolicyModel.Band;
import com.placehub.application.policy.PolicyModel.PlacementState;
import com.placehub.application.policy.PolicyModel.Rules;
import com.placehub.application.policy.PolicyRepositories.PolicyAuditRepository;
import com.placehub.application.policy.PolicyRepositories.PolicyVersionRepository;
import com.placehub.common.error.ApiException;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.paging.Paging;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Policy versions (append-only), the simulator and the audit trail (spec 20.3). */
@Service
public class PolicyService {

    private static final TypeReference<List<BandDto>> BANDS = new TypeReference<>() {
    };

    private final PolicyVersionRepository versions;
    private final PolicyAuditRepository audit;
    private final PolicyEvaluator evaluator;
    private final JsonMapper json;
    private final Clock clock;

    public PolicyService(PolicyVersionRepository versions, PolicyAuditRepository audit, PolicyEvaluator evaluator,
                         JsonMapper json, Clock clock) {
        this.versions = versions;
        this.audit = audit;
        this.evaluator = evaluator;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Rules activeRules() {
        return toRules(activeVersion());
    }

    @Transactional(readOnly = true)
    public PolicyResponse active() {
        return toResponse(activeVersion());
    }

    @Transactional(readOnly = true)
    public PageResponse<PolicyResponse> history(Integer page, Integer size) {
        var pageable = Paging.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.of(versions.findAllByOrderByVersionDesc(pageable), this::toResponse);
    }

    /** Creates and activates a new version; the previous version is kept, deactivated. */
    @Transactional
    public PolicyResponse update(UUID adminId, PolicyRequest req) {
        List<Band> bands = req.bands().stream().map(BandDto::toBand).toList();
        String consentText = req.consentText() == null ? "" : req.consentText();
        requireValid(PolicyValidator.validate(bands, req.maxActiveConsents(), consentText, req.changeNote()));
        PolicyVersion previous = activeVersion();
        PolicyResponse before = toResponse(previous);
        previous.deactivate();
        versions.saveAndFlush(previous);
        PolicyVersion next = new PolicyVersion(UUID.randomUUID(), versions.maxVersion() + 1, writeBands(bands),
                req.appliesToInternships(), req.maxActiveConsents(),
                req.resumeRequired() == null || req.resumeRequired(),
                req.withdrawOtherApplicationsOnLock() == null || req.withdrawOtherApplicationsOnLock(),
                consentText, clock.instant(), adminId, req.changeNote().trim(), clock.instant());
        versions.saveAndFlush(next);
        PolicyResponse after = toResponse(next);
        audit.save(new PolicyAuditEntry(UUID.randomUUID(), adminId, PolicyAuditEntry.Action.POLICY_VERSION_CREATED,
                null, json.writeValueAsString(before), json.writeValueAsString(after), req.changeNote().trim(),
                clock.instant()));
        return after;
    }

    @Transactional(readOnly = true)
    public EvaluationDto simulate(SimulateRequest req) {
        Rules rules = req.policy() == null ? activeRules() : draftRules(req.policy());
        PlacementState state = new PlacementState(req.currentCtc() == null ? null
                : req.currentCtc().setScale(2, RoundingMode.HALF_UP), req.locked());
        return EvaluationDto.of(evaluator.evaluate(state, req.jobCtc(), req.employmentType(), rules));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditDto> audit(Integer page, Integer size) {
        var pageable = Paging.of(page, size, null, Map.of(), Sort.unsorted());
        return PageResponse.of(audit.findAllByOrderByCreatedAtDesc(pageable), a -> new AuditDto(a.getId(),
                a.getActorUserId(), a.getAction().name(), a.getStudentUserId(), a.getBeforeJson(), a.getAfterJson(),
                a.getReason(), a.getCreatedAt()));
    }

    public void recordAudit(UUID actorId, PolicyAuditEntry.Action action, UUID studentId, Object before, Object after,
                            String reason) {
        audit.save(new PolicyAuditEntry(UUID.randomUUID(), actorId, action, studentId,
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after), reason, clock.instant()));
    }

    Rules draftRules(PolicyDraft d) {
        List<Band> bands = d.bands().stream().map(BandDto::toBand).toList();
        String consentText = d.consentText() == null ? "" : d.consentText();
        requireValid(PolicyValidator.validate(bands, d.maxActiveConsents(), consentText, null));
        return new Rules(0, bands, d.appliesToInternships(), d.maxActiveConsents(),
                d.resumeRequired() == null || d.resumeRequired(),
                d.withdrawOtherApplicationsOnLock() == null || d.withdrawOtherApplicationsOnLock(),
                consentText, clock.instant(), null, "draft");
    }

    private PolicyVersion activeVersion() {
        return versions.findByActiveTrue()
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No active placement policy"));
    }

    private static void requireValid(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid placement policy: " + String.join("; ", errors),
                    Map.of("errors", errors));
        }
    }

    Rules toRules(PolicyVersion v) {
        return new Rules(v.getVersion(), readBands(v.getBandsJson()).stream().map(BandDto::toBand).toList(),
                v.isAppliesToInternships(), v.getMaxActiveConsents(), v.isResumeRequired(), v.isWithdrawOthersOnLock(),
                v.getConsentText(), v.getEffectiveFrom(), v.getChangedByUserId(), v.getChangeNote());
    }

    private PolicyResponse toResponse(PolicyVersion v) {
        return new PolicyResponse(v.getId(), v.getVersion(), v.isActive(), readBands(v.getBandsJson()),
                v.isAppliesToInternships(), v.getMaxActiveConsents(), v.isResumeRequired(), v.isWithdrawOthersOnLock(),
                v.getConsentText(), v.getEffectiveFrom(), v.getChangedByUserId(), v.getChangeNote(), v.getCreatedAt());
    }

    private List<BandDto> readBands(String text) {
        return json.readValue(text, BANDS);
    }

    private String writeBands(List<Band> bands) {
        return json.writeValueAsString(bands.stream().map(BandDto::of).toList());
    }
}
