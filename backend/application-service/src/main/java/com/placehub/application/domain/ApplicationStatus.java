package com.placehub.application.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Application lifecycle (spec section 5.3):
 * APPLIED -> UNDER_REVIEW, SHORTLISTED, REJECTED, WITHDRAWN;
 * UNDER_REVIEW -> SHORTLISTED, REJECTED, WITHDRAWN;
 * SHORTLISTED -> SELECTED, REJECTED, WITHDRAWN;
 * SELECTED, REJECTED, WITHDRAWN are terminal.
 */
public enum ApplicationStatus {
    APPLIED, UNDER_REVIEW, SHORTLISTED, SELECTED, REJECTED, WITHDRAWN;

    public Set<ApplicationStatus> nextStatuses() {
        return switch (this) {
            case APPLIED -> EnumSet.of(UNDER_REVIEW, SHORTLISTED, REJECTED, WITHDRAWN);
            case UNDER_REVIEW -> EnumSet.of(SHORTLISTED, REJECTED, WITHDRAWN);
            case SHORTLISTED -> EnumSet.of(SELECTED, REJECTED, WITHDRAWN);
            case SELECTED, REJECTED, WITHDRAWN -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return target != null && nextStatuses().contains(target);
    }

    public boolean isTerminal() {
        return nextStatuses().isEmpty();
    }
}
