package com.placehub.application.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.placehub.application.domain.ApplicationStatus.APPLIED;
import static com.placehub.application.domain.ApplicationStatus.REJECTED;
import static com.placehub.application.domain.ApplicationStatus.SELECTED;
import static com.placehub.application.domain.ApplicationStatus.SHORTLISTED;
import static com.placehub.application.domain.ApplicationStatus.UNDER_REVIEW;
import static com.placehub.application.domain.ApplicationStatus.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

/** Exhaustive check of every (from, to) pair against the spec's transition table. */
class ApplicationStatusTest {

    static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = Map.of(
            APPLIED, EnumSet.of(UNDER_REVIEW, SHORTLISTED, REJECTED, WITHDRAWN),
            UNDER_REVIEW, EnumSet.of(SHORTLISTED, REJECTED, WITHDRAWN),
            SHORTLISTED, EnumSet.of(SELECTED, REJECTED, WITHDRAWN),
            SELECTED, EnumSet.noneOf(ApplicationStatus.class),
            REJECTED, EnumSet.noneOf(ApplicationStatus.class),
            WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));

    static Stream<Arguments> everyPair() {
        List<Arguments> pairs = new ArrayList<>();
        for (ApplicationStatus from : ApplicationStatus.values()) {
            for (ApplicationStatus to : ApplicationStatus.values()) {
                pairs.add(Arguments.of(from, to, ALLOWED.get(from).contains(to)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("everyPair")
    void transitionMatchesSpec(ApplicationStatus from, ApplicationStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @MethodSource("everyPair")
    void nullTargetNeverAllowed(ApplicationStatus from, ApplicationStatus ignored, boolean ignored2) {
        assertThat(from.canTransitionTo(null)).isFalse();
        assertThat(from.isTerminal()).isEqualTo(ALLOWED.get(from).isEmpty());
    }
}
