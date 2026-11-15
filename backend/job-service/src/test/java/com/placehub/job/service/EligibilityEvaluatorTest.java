package com.placehub.job.service;

import com.placehub.job.domain.Branch;
import com.placehub.job.domain.JobStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityEvaluatorTest {

    static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    final EligibilityEvaluator evaluator = new EligibilityEvaluator(Clock.fixed(NOW, ZoneOffset.UTC));

    static EligibilityEvaluator.Criteria job(JobStatus status, Instant deadline, String minCgpa, int maxBacklogs,
                                             Set<Branch> branches, Set<Integer> years) {
        return new EligibilityEvaluator.Criteria(status, deadline, new BigDecimal(minCgpa), maxBacklogs, branches,
                years);
    }

    static EligibilityEvaluator.Criteria open() {
        return job(JobStatus.PUBLISHED, NOW.plusSeconds(3600), "0", 0, Set.of(), Set.of());
    }

    static EligibilityEvaluator.Candidate student(Branch b, int year, String cgpa, int backlogs) {
        return new EligibilityEvaluator.Candidate(b, year, new BigDecimal(cgpa), backlogs);
    }

    @Test
    void eligibleStudentHasNoReasons() {
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusSeconds(60), "7.50", 0,
                EnumSet.of(Branch.CSE, Branch.ISE), Set.of(2027)), student(Branch.CSE, 2027, "8.60", 0))).isEmpty();
    }

    @Test
    void notPublishedIsRejected() {
        for (JobStatus s : new JobStatus[]{JobStatus.DRAFT, JobStatus.CLOSED}) {
            assertThat(evaluator.evaluate(job(s, NOW.plusSeconds(60), "0", 0, Set.of(), Set.of()),
                    student(Branch.CSE, 2027, "9", 0)))
                    .containsExactly("This job is not open for applications");
        }
    }

    @Test
    void deadlineBoundaryIsExclusive() {
        var s = student(Branch.CSE, 2027, "9", 0);
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW, "0", 0, Set.of(), Set.of()), s))
                .containsExactly("The application deadline has passed");
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.minusMillis(1), "0", 0, Set.of(), Set.of()), s))
                .containsExactly("The application deadline has passed");
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusMillis(1), "0", 0, Set.of(), Set.of()), s))
                .isEmpty();
    }

    @Test
    void cgpaBelowMinimumUsesSpecMessage() {
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusSeconds(60), "7.5", 0, Set.of(), Set.of()),
                student(Branch.CSE, 2027, "6.9", 0)))
                .containsExactly("Minimum CGPA required is 7.50; yours is 6.90");
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusSeconds(60), "7.5", 0, Set.of(), Set.of()),
                student(Branch.CSE, 2027, "7.50", 0))).isEmpty();
    }

    @Test
    void backlogsAboveMaximumAreRejected() {
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusSeconds(60), "0", 1, Set.of(), Set.of()),
                student(Branch.CSE, 2027, "8", 2)))
                .containsExactly("Maximum backlogs allowed is 1; you have 2");
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusSeconds(60), "0", 2, Set.of(), Set.of()),
                student(Branch.CSE, 2027, "8", 2))).isEmpty();
    }

    @Test
    void branchMustBeListedWhenListIsNonEmpty() {
        List<String> reasons = evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusSeconds(60), "0", 0,
                EnumSet.of(Branch.EEE, Branch.ECE, Branch.ETE), Set.of()), student(Branch.CSE, 2027, "8", 0));
        assertThat(reasons).containsExactly("Your branch (CSE) is not eligible; eligible branches: ECE, EEE, ETE");
    }

    @Test
    void graduationYearMustBeListedWhenListIsNonEmpty() {
        assertThat(evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusSeconds(60), "0", 0, Set.of(),
                Set.of(2028, 2027)), student(Branch.CSE, 2026, "8", 0)))
                .containsExactly("Graduation year 2026 is not eligible; eligible years: 2027, 2028");
    }

    @Test
    void emptyBranchAndYearListsMeanAll() {
        for (Branch b : Branch.values()) {
            assertThat(evaluator.evaluate(open(), student(b, 2031, "0", 0))).isEmpty();
        }
    }

    @Test
    void allFailingReasonsAreReturnedTogether() {
        List<String> reasons = evaluator.evaluate(job(JobStatus.CLOSED, NOW.minusSeconds(1), "7.50", 0,
                EnumSet.of(Branch.CSE, Branch.ISE, Branch.AIML), Set.of(2027)), student(Branch.ME, 2026, "6.40", 2));
        assertThat(reasons).hasSize(6).containsExactly(
                "This job is not open for applications",
                "The application deadline has passed",
                "Minimum CGPA required is 7.50; yours is 6.40",
                "Maximum backlogs allowed is 0; you have 2",
                "Your branch (ME) is not eligible; eligible branches: CSE, ISE, AIML",
                "Graduation year 2026 is not eligible; eligible years: 2027");
    }

    @Test
    void seededStudent2FailsGraduateSoftwareEngineerOnThreeReasons() {
        List<String> reasons = evaluator.evaluate(job(JobStatus.PUBLISHED, NOW.plusSeconds(86400), "7.50", 0,
                EnumSet.of(Branch.CSE, Branch.ISE, Branch.AIML), Set.of(2027)), student(Branch.ME, 2027, "6.40", 2));
        assertThat(reasons).hasSize(3);
    }
}
