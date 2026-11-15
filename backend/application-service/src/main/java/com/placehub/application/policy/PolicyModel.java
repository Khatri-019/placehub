package com.placehub.application.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pure value types of the placement policy (spec 20.3). No Spring, no persistence. */
public final class PolicyModel {

    private PolicyModel() {
    }

    public enum Action { BLOCK, REQUIRE_CONSENT, ALLOW }

    public enum Decision { ALLOW, REQUIRE_CONSENT, BLOCK }

    /** One multiplier band: {@code [fromMultiplier, toMultiplier)}; a null {@code toMultiplier} means infinity. */
    public record Band(BigDecimal fromMultiplier, BigDecimal toMultiplier, Action action, boolean lockOnPlacement) {

        public boolean contains(BigDecimal jobCtc, BigDecimal currentCtc) {
            boolean aboveFrom = jobCtc.compareTo(currentCtc.multiply(fromMultiplier)) >= 0;
            boolean belowTo = toMultiplier == null || jobCtc.compareTo(currentCtc.multiply(toMultiplier)) < 0;
            return aboveFrom && belowTo;
        }

        /** Stable human label stored as the application's band snapshot, e.g. "[1.50, 2.00) REQUIRE_CONSENT lock". */
        public String label() {
            return "[" + two(fromMultiplier) + ", " + (toMultiplier == null ? "inf" : two(toMultiplier)) + ") "
                    + action + (lockOnPlacement ? " lock" : "");
        }
    }

    /** An immutable policy version as the evaluator sees it. */
    public record Rules(int version, List<Band> bands, boolean appliesToInternships, Integer maxActiveConsents,
                        boolean resumeRequired, boolean withdrawOtherApplicationsOnLock, String consentText,
                        Instant effectiveFrom, UUID changedByUserId, String changeNote) {

        public Rules {
            bands = List.copyOf(bands);
        }
    }

    /** A student's placement standing: unplaced when {@code currentCtc} is null. */
    public record PlacementState(BigDecimal currentCtc, boolean locked) {

        public static final PlacementState UNPLACED = new PlacementState(null, false);

        public boolean placed() {
            return currentCtc != null;
        }
    }

    public enum ReasonCode { POLICY_BLOCKED, STUDENT_LOCKED }

    public record Reason(ReasonCode code, String message) {
    }

    /** Evaluator output (spec 20.3): decision, every reason, the matched band, the version and the ratio (2 dp). */
    public record Evaluation(Decision decision, List<Reason> reasons, Band matchedBand, int policyVersion,
                             BigDecimal ratio, Instant evaluatedAt) {

        public Evaluation {
            reasons = List.copyOf(reasons);
        }

        public boolean lockOnPlacement() {
            return matchedBand != null && matchedBand.lockOnPlacement() && decision != Decision.BLOCK;
        }
    }

    static String two(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
