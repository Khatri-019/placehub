package com.placehub.job.dto;

import com.placehub.job.domain.Branch;
import com.placehub.job.domain.EmploymentType;
import com.placehub.job.domain.ScreeningMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record JobRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank @Size(max = 150) String location,
        @NotNull EmploymentType employmentType,
        @Size(max = 100) String ctc,
        @DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2) BigDecimal ctcLpa,
        @DecimalMin("0.00") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2) BigDecimal minCgpa,
        @Min(0) @Max(50) Integer maxBacklogs,
        Set<@NotNull Branch> eligibleBranches,
        Set<@NotNull @Min(2020) @Max(2035) Integer> eligibleGraduationYears,
        @NotNull Instant deadline,
        ScreeningMode screeningMode,
        @DecimalMin("0.00") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2) BigDecimal autoShortlistMinCgpa,
        @Min(0) @Max(50) Integer autoShortlistMaxBacklogs) {

    /** v2-compatible constructor (manual screening). */
    public JobRequest(String title, String description, String location, EmploymentType employmentType, String ctc,
                      BigDecimal ctcLpa, BigDecimal minCgpa, Integer maxBacklogs, Set<Branch> eligibleBranches,
                      Set<Integer> eligibleGraduationYears, Instant deadline) {
        this(title, description, location, employmentType, ctc, ctcLpa, minCgpa, maxBacklogs, eligibleBranches,
                eligibleGraduationYears, deadline, null, null, null);
    }
}
