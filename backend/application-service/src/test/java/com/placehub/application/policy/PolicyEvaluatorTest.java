package com.placehub.application.policy;

import com.placehub.application.policy.PolicyModel.Action;
import com.placehub.application.policy.PolicyModel.Band;
import com.placehub.application.policy.PolicyModel.Decision;
import com.placehub.application.policy.PolicyModel.Evaluation;
import com.placehub.application.policy.PolicyModel.PlacementState;
import com.placehub.application.policy.PolicyModel.ReasonCode;
import com.placehub.application.policy.PolicyModel.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEvaluatorTest {

    static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    final PolicyEvaluator evaluator = new PolicyEvaluator(Clock.fixed(NOW, ZoneOffset.UTC));

    static Rules defaults(boolean internships) {
        return new Rules(7, List.of(
                new Band(BigDecimal.ZERO, new BigDecimal("1.5"), Action.BLOCK, false),
                new Band(new BigDecimal("1.5"), new BigDecimal("2.0"), Action.REQUIRE_CONSENT, true),
                new Band(new BigDecimal("2.0"), null, Action.ALLOW, false)),
                internships, null, true, true, "text", NOW, null, "v");
    }

    Evaluation eval(String current, String job, String type) {
        return evaluator.evaluate(new PlacementState(current == null ? null : new BigDecimal(current), false),
                job == null ? null : new BigDecimal(job), type, defaults(false));
    }

    /** Placed at 12.00: boundaries at 18.00 (1.5x) and 24.00 (2.0x); exactly on a boundary falls in the upper band. */
    @ParameterizedTest(name = "12.00 -> {0} LPA = {1}")
    @CsvSource({
            "0.01,BLOCK", "8.00,BLOCK", "12.00,BLOCK", "17.99,BLOCK",
            "18.00,REQUIRE_CONSENT", "18.01,REQUIRE_CONSENT", "20.00,REQUIRE_CONSENT", "23.99,REQUIRE_CONSENT",
            "24.00,ALLOW", "24.01,ALLOW", "30.00,ALLOW", "9999.99,ALLOW"})
    void everyBandAndExactBoundary(String jobCtc, Decision expected) {
        Evaluation e = eval("12.00", jobCtc, "FULL_TIME");
        assertThat(e.decision()).isEqualTo(expected);
        assertThat(e.policyVersion()).isEqualTo(7);
        assertThat(e.evaluatedAt()).isEqualTo(NOW);
    }

    @Test
    void ratioIsReportedTo2DecimalsAndBandIsMatched() {
        Evaluation e = eval("12.00", "20.00", "FULL_TIME");
        assertThat(e.ratio()).isEqualByComparingTo("1.67");
        assertThat(e.matchedBand().label()).isEqualTo("[1.50, 2.00) REQUIRE_CONSENT lock");
        assertThat(e.lockOnPlacement()).isTrue();
        Evaluation open = eval("12.00", "30.00", "FULL_TIME");
        assertThat(open.ratio()).isEqualByComparingTo("2.50");
        assertThat(open.lockOnPlacement()).isFalse();
    }

    @Test
    void exactBoundaryUsesExactArithmeticNotTheRoundedRatio() {
        // 17.99 / 12 = 1.4991..., rounds to 1.50 but must still be BLOCK; 18 / 12 = 1.5 exactly is consent.
        assertThat(eval("12.00", "17.99", "FULL_TIME").ratio()).isEqualByComparingTo("1.50");
        assertThat(eval("12.00", "17.99", "FULL_TIME").decision()).isEqualTo(Decision.BLOCK);
        assertThat(eval("12.00", "18.00", "FULL_TIME").decision()).isEqualTo(Decision.REQUIRE_CONSENT);
        // Odd CTCs: 7.33 * 2 = 14.66.
        assertThat(eval("7.33", "14.65", "FULL_TIME").decision()).isEqualTo(Decision.REQUIRE_CONSENT);
        assertThat(eval("7.33", "14.66", "FULL_TIME").decision()).isEqualTo(Decision.ALLOW);
    }

    @Test
    void blockedReasonExplainsTheMinimum() {
        Evaluation e = eval("12.00", "8.00", "FULL_TIME");
        assertThat(e.reasons()).singleElement().satisfies(r -> {
            assertThat(r.code()).isEqualTo(ReasonCode.POLICY_BLOCKED);
            assertThat(r.message()).contains("8.00 LPA").contains("0.67x").contains("1.50x (18.00 LPA)");
        });
    }

    @Test
    void unplacedStudentsHaveNoCtcRestrictions() {
        for (String job : new String[]{"0.01", "5.00", "100.00"}) {
            Evaluation e = eval(null, job, "FULL_TIME");
            assertThat(e.decision()).isEqualTo(Decision.ALLOW);
            assertThat(e.matchedBand()).isNull();
            assertThat(e.ratio()).isNull();
            assertThat(e.lockOnPlacement()).isFalse();
        }
    }

    @Test
    void lockedStudentsAreBlockedFromEverythingIncludingInternships() {
        for (String type : new String[]{"FULL_TIME", "INTERNSHIP"}) {
            Evaluation e = evaluator.evaluate(new PlacementState(new BigDecimal("12"), true), new BigDecimal("50"),
                    type, defaults(false));
            assertThat(e.decision()).isEqualTo(Decision.BLOCK);
            assertThat(e.reasons()).extracting(PolicyModel.Reason::code).containsExactly(ReasonCode.STUDENT_LOCKED);
        }
        Evaluation unplacedLocked = evaluator.evaluate(new PlacementState(null, true), new BigDecimal("50"),
                "FULL_TIME", defaults(false));
        assertThat(unplacedLocked.decision()).isEqualTo(Decision.BLOCK);
    }

    @Test
    void internshipToggle() {
        // Exempt by default: even a low stipend and a missing CTC are allowed.
        assertThat(eval("12.00", null, "INTERNSHIP").decision()).isEqualTo(Decision.ALLOW);
        assertThat(eval("12.00", "1.00", "INTERNSHIP").decision()).isEqualTo(Decision.ALLOW);
        // When the policy covers internships the bands apply, and a missing CTC blocks.
        PlacementState placed = new PlacementState(new BigDecimal("12.00"), false);
        assertThat(evaluator.evaluate(placed, new BigDecimal("1.00"), "INTERNSHIP", defaults(true)).decision())
                .isEqualTo(Decision.BLOCK);
        assertThat(evaluator.evaluate(placed, new BigDecimal("30.00"), "INTERNSHIP", defaults(true)).decision())
                .isEqualTo(Decision.ALLOW);
        assertThat(evaluator.evaluate(placed, null, "INTERNSHIP", defaults(true)).reasons())
                .extracting(PolicyModel.Reason::message).containsExactly(PolicyEvaluator.CTC_NOT_CONFIGURED);
    }

    @Test
    void fullTimeJobWithoutCtcIsBlockedEvenForUnplacedStudents() {
        for (String current : new String[]{null, "12.00"}) {
            Evaluation e = eval(current, null, "FULL_TIME");
            assertThat(e.decision()).isEqualTo(Decision.BLOCK);
            assertThat(e.reasons()).extracting(PolicyModel.Reason::message)
                    .containsExactly("Job CTC not configured; contact the placement office");
        }
    }

    @Test
    void customPolicyWithLockingAllowBand() {
        Rules rules = new Rules(9, List.of(
                new Band(BigDecimal.ZERO, new BigDecimal("1.25"), Action.BLOCK, false),
                new Band(new BigDecimal("1.25"), null, Action.ALLOW, true)), false, null, true, true, "", NOW, null, "x");
        Evaluation e = evaluator.evaluate(new PlacementState(new BigDecimal("10"), false), new BigDecimal("12.50"),
                "FULL_TIME", rules);
        assertThat(e.decision()).isEqualTo(Decision.ALLOW);
        assertThat(e.lockOnPlacement()).isTrue();
        assertThat(e.policyVersion()).isEqualTo(9);
    }
}
