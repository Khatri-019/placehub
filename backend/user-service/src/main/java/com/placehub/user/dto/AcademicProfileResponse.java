package com.placehub.user.dto;

import com.placehub.user.domain.AccountStatus;
import com.placehub.user.domain.Branch;

import java.math.BigDecimal;
import java.util.UUID;

public record AcademicProfileResponse(UUID userId, String fullName, String email, Branch branch, int graduationYear,
                                      BigDecimal cgpa, int backlogCount, AccountStatus status) {
}
