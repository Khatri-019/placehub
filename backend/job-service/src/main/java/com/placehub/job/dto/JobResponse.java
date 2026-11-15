package com.placehub.job.dto;

import com.placehub.job.domain.Branch;
import com.placehub.job.domain.EmploymentType;
import com.placehub.job.domain.JobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobResponse(UUID id, UUID companyId, String companyName, UUID recruiterOwnerUserId, String title,
                          String description, String location, EmploymentType employmentType, String ctc,
                          BigDecimal minCgpa, int maxBacklogs, List<Branch> eligibleBranches,
                          List<Integer> eligibleGraduationYears, Instant deadline, JobStatus status,
                          Instant createdAt, Instant updatedAt) {
}
