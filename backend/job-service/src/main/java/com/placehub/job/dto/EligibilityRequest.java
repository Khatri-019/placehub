package com.placehub.job.dto;

import com.placehub.job.domain.Branch;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record EligibilityRequest(@NotNull Branch branch, @NotNull Integer graduationYear, @NotNull BigDecimal cgpa,
                                 @NotNull Integer backlogCount) {
}
