package com.placehub.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.placehub.user.domain.AccountStatus;
import com.placehub.user.domain.Branch;
import com.placehub.user.domain.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(UUID id, String fullName, String email, Role role, AccountStatus status,
                           Instant createdAt, StudentDetailsDto studentDetails,
                           RecruiterDetailsDto recruiterDetails) {

    public record StudentDetailsDto(String collegeName, Branch branch, int graduationYear, BigDecimal cgpa,
                                    int backlogCount) {
    }

    public record RecruiterDetailsDto(String companyName, String designation) {
    }
}
