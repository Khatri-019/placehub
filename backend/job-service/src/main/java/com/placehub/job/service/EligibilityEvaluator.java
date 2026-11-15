package com.placehub.job.service;

import com.placehub.job.domain.Branch;
import com.placehub.job.domain.JobStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure eligibility rules (no Spring). Every rule is evaluated so the student sees all failing reasons at once.
 */
public final class EligibilityEvaluator {

    /** Job-side criteria. Empty branch/year sets mean "all". */
    public record Criteria(JobStatus status, Instant deadline, BigDecimal minCgpa, int maxBacklogs,
                           Set<Branch> eligibleBranches, Set<Integer> eligibleGraduationYears) {
    }

    /** Trusted student attributes (fetched by Application Service from User Service). */
    public record Candidate(Branch branch, int graduationYear, BigDecimal cgpa, int backlogCount) {
    }

    private final Clock clock;

    public EligibilityEvaluator(Clock clock) {
        this.clock = clock;
    }

    public List<String> evaluate(Criteria job, Candidate student) {
        List<String> reasons = new ArrayList<>();
        if (job.status() != JobStatus.PUBLISHED) {
            reasons.add("This job is not open for applications");
        }
        if (!clock.instant().isBefore(job.deadline())) {
            reasons.add("The application deadline has passed");
        }
        BigDecimal min = job.minCgpa() == null ? BigDecimal.ZERO : job.minCgpa();
        if (student.cgpa().compareTo(min) < 0) {
            reasons.add("Minimum CGPA required is " + twoDp(min) + "; yours is " + twoDp(student.cgpa()));
        }
        if (student.backlogCount() > job.maxBacklogs()) {
            reasons.add("Maximum backlogs allowed is " + job.maxBacklogs() + "; you have " + student.backlogCount());
        }
        Set<Branch> branches = job.eligibleBranches();
        if (branches != null && !branches.isEmpty() && !branches.contains(student.branch())) {
            reasons.add("Your branch (" + student.branch() + ") is not eligible; eligible branches: "
                    + branches.stream().sorted().map(Enum::name).collect(Collectors.joining(", ")));
        }
        Set<Integer> years = job.eligibleGraduationYears();
        if (years != null && !years.isEmpty() && !years.contains(student.graduationYear())) {
            reasons.add("Graduation year " + student.graduationYear() + " is not eligible; eligible years: "
                    + years.stream().sorted().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        return reasons;
    }

    private static String twoDp(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
