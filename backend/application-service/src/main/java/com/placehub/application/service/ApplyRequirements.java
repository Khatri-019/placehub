package com.placehub.application.service;

import com.placehub.application.client.ClientDtos;
import com.placehub.application.client.DependencyGateway;
import com.placehub.application.policy.ConsentText;
import com.placehub.application.policy.PlacementService;
import com.placehub.application.policy.PolicyEvaluator;
import com.placehub.application.policy.PolicyModel.Decision;
import com.placehub.application.policy.PolicyModel.Evaluation;
import com.placehub.application.policy.PolicyModel.PlacementState;
import com.placehub.application.policy.PolicyModel.Reason;
import com.placehub.application.policy.PolicyModel.ReasonCode;
import com.placehub.application.policy.PolicyModel.Rules;
import com.placehub.application.policy.PolicyService;
import com.placehub.application.repository.ApplicationRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates every apply requirement in the order the spec fixes (20.3): Job Service eligibility, resume, placement
 * policy (lock, band, consent, consent limit). Shared by apply and the preview so both always agree.
 */
@Component
public class ApplyRequirements {

    static final String CONSENT_MISSING = "This job needs your consent under the placement policy";
    static final String CONSENT_SIGNATURE = "Type your full name exactly as registered to sign the consent";

    /** Consent as submitted by the student; {@code null} when absent. */
    public record ConsentInput(Boolean accepted, String signedName) {
    }

    /** Everything decided before persisting; {@code violations} is empty when the application may be created. */
    public record Assessment(ClientDtos.AcademicProfile profile, ClientDtos.EligibilityResult eligibility,
                             ClientDtos.JobSummary job, Rules rules, PlacementState state, Evaluation evaluation,
                             String renderedConsent, boolean consentAccepted, Violations violations) {

        public boolean consentNeeded() {
            return evaluation.decision() == Decision.REQUIRE_CONSENT;
        }

        public Map<String, Object> consentDetails() {
            if (!consentNeeded()) {
                return null;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("renderedText", renderedConsent);
            m.put("policyVersion", evaluation.policyVersion());
            m.put("band", evaluation.matchedBand() == null ? null : evaluation.matchedBand().label());
            m.put("ratio", evaluation.ratio());
            return m;
        }
    }

    private final DependencyGateway dependencies;
    private final PolicyService policies;
    private final PlacementService placements;
    private final PolicyEvaluator evaluator;
    private final ApplicationRepository applications;

    public ApplyRequirements(DependencyGateway dependencies, PolicyService policies, PlacementService placements,
                             PolicyEvaluator evaluator, ApplicationRepository applications) {
        this.dependencies = dependencies;
        this.policies = policies;
        this.placements = placements;
        this.evaluator = evaluator;
        this.applications = applications;
    }

    public Assessment assess(UUID studentId, ClientDtos.AcademicProfile profile, UUID jobId, ConsentInput consent) {
        Violations v = new Violations();
        ClientDtos.EligibilityResult eligibility = dependencies.validateEligibility(jobId,
                new ClientDtos.EligibilityCheck(profile.branch(), profile.graduationYear(), profile.cgpa(),
                        profile.backlogCount()));
        if (!eligibility.eligible()) {
            v.addAll(Violations.Code.NOT_ELIGIBLE, eligibility.reasons() == null ? List.of() : eligibility.reasons());
        }
        Rules rules = policies.activeRules();
        if (rules.resumeRequired() && profile.currentResumeId() == null) {
            v.add(Violations.Code.RESUME_REQUIRED, Violations.RESUME_REQUIRED_MESSAGE);
        }
        ClientDtos.JobSummary job = dependencies.jobSummary(jobId);
        PlacementState state = placements.state(studentId);
        Evaluation evaluation = evaluator.evaluate(state, job.ctcLpa(), job.employmentType(), rules);
        String rendered = null;
        boolean accepted = false;
        switch (evaluation.decision()) {
            case BLOCK -> {
                for (Reason r : evaluation.reasons()) {
                    v.add(r.code() == ReasonCode.STUDENT_LOCKED ? Violations.Code.STUDENT_LOCKED
                            : Violations.Code.POLICY_BLOCKED, r.message());
                }
            }
            case REQUIRE_CONSENT -> {
                rendered = ConsentText.render(rules.consentText(), job.companyName(), job.title(), job.ctcLpa(),
                        state.currentCtc());
                if (rules.maxActiveConsents() != null
                        && applications.countActiveConsentApplications(studentId) >= rules.maxActiveConsents()) {
                    v.add(Violations.Code.CONSENT_LIMIT_REACHED, "You already have " + rules.maxActiveConsents()
                            + " open application(s) that needed consent, the most the placement policy allows");
                }
                if (consent == null || !Boolean.TRUE.equals(consent.accepted())) {
                    v.add(Violations.Code.CONSENT_REQUIRED, CONSENT_MISSING);
                } else if (!ConsentText.signatureMatches(consent.signedName(), profile.fullName())) {
                    v.add(Violations.Code.CONSENT_REQUIRED, CONSENT_SIGNATURE);
                } else {
                    accepted = true;
                }
            }
            case ALLOW -> { }
        }
        Assessment a = new Assessment(profile, eligibility, job, rules, state, evaluation, rendered, accepted, v);
        if (a.consentNeeded()) {
            v.detail("consent", a.consentDetails());
        }
        return a;
    }
}
