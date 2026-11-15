package com.placehub.application.policy;

import com.placehub.application.policy.PolicyModel.Action;
import com.placehub.application.policy.PolicyModel.Band;
import com.placehub.application.policy.PolicyModel.Decision;
import com.placehub.application.policy.PolicyModel.Evaluation;
import com.placehub.application.policy.PolicyModel.PlacementState;
import com.placehub.application.policy.PolicyModel.Reason;
import com.placehub.application.policy.PolicyModel.ReasonCode;
import com.placehub.application.policy.PolicyModel.Rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;

/**
 * Pure placement-policy evaluation (spec 20.3). Order of rules:
 * <ol>
 *   <li>locked student → BLOCK (STUDENT_LOCKED)</li>
 *   <li>internship and the policy does not cover internships → ALLOW</li>
 *   <li>job without numeric CTC → BLOCK ("Job CTC not configured")</li>
 *   <li>unplaced student → ALLOW</li>
 *   <li>otherwise the band containing {@code jobCtc / currentCtc}, matched with exact arithmetic
 *       ({@code jobCtc >= currentCtc * from} and {@code jobCtc < currentCtc * to}), so boundaries are inclusive on
 *       {@code from}.</li>
 * </ol>
 */
public final class PolicyEvaluator {

    public static final String CTC_NOT_CONFIGURED = "Job CTC not configured; contact the placement office";
    public static final String LOCKED = "You are locked under the placement policy after your placement. "
            + "Contact the placement office to be unlocked.";

    private final Clock clock;

    public PolicyEvaluator(Clock clock) {
        this.clock = clock;
    }

    public Evaluation evaluate(PlacementState state, BigDecimal jobCtc, String employmentType, Rules rules) {
        PlacementState s = state == null ? PlacementState.UNPLACED : state;
        if (s.locked()) {
            return result(Decision.BLOCK, List.of(new Reason(ReasonCode.STUDENT_LOCKED, LOCKED)), null, rules, null);
        }
        boolean internship = "INTERNSHIP".equals(employmentType);
        if (internship && !rules.appliesToInternships()) {
            return result(Decision.ALLOW, List.of(), null, rules, null);
        }
        if (jobCtc == null || jobCtc.signum() <= 0) {
            return result(Decision.BLOCK, List.of(new Reason(ReasonCode.POLICY_BLOCKED, CTC_NOT_CONFIGURED)), null,
                    rules, null);
        }
        if (!s.placed()) {
            return result(Decision.ALLOW, List.of(), null, rules, null);
        }
        BigDecimal ratio = jobCtc.divide(s.currentCtc(), 2, RoundingMode.HALF_UP);
        Band band = rules.bands().stream().filter(b -> b.contains(jobCtc, s.currentCtc())).findFirst().orElse(null);
        if (band == null) {
            // Unreachable for a validated policy (bands cover [0, inf)); fail closed.
            return result(Decision.BLOCK, List.of(new Reason(ReasonCode.POLICY_BLOCKED,
                    "No placement policy band covers this job; contact the placement office")), null, rules, ratio);
        }
        return switch (band.action()) {
            case ALLOW -> result(Decision.ALLOW, List.of(), band, rules, ratio);
            case REQUIRE_CONSENT -> result(Decision.REQUIRE_CONSENT, List.of(), band, rules, ratio);
            case BLOCK -> result(Decision.BLOCK, List.of(new Reason(ReasonCode.POLICY_BLOCKED,
                    blockedMessage(jobCtc, s.currentCtc(), ratio, rules))), band, rules, ratio);
        };
    }

    private static String blockedMessage(BigDecimal jobCtc, BigDecimal currentCtc, BigDecimal ratio, Rules rules) {
        BigDecimal minimum = rules.bands().stream()
                .filter(b -> b.action() != Action.BLOCK && b.fromMultiplier().compareTo(ratio) > 0)
                .map(Band::fromMultiplier).findFirst().orElse(null);
        String base = "This job pays " + PolicyModel.two(jobCtc) + " LPA (" + ratio.toPlainString()
                + "x your current " + PolicyModel.two(currentCtc) + " LPA)";
        return minimum == null
                ? base + "; the placement policy does not allow it"
                : base + "; the placement policy requires at least " + PolicyModel.two(minimum) + "x ("
                + PolicyModel.two(currentCtc.multiply(minimum)) + " LPA)";
    }

    private Evaluation result(Decision decision, List<Reason> reasons, Band band, Rules rules, BigDecimal ratio) {
        return new Evaluation(decision, reasons, band, rules.version(), ratio, clock.instant());
    }
}
