package com.placehub.job.dto;

import com.placehub.job.domain.EmploymentType;
import com.placehub.job.domain.JobStatus;
import com.placehub.job.domain.ScreeningMode;

import java.math.BigDecimal;

import java.time.Instant;
import java.util.UUID;

/** Internal contract used by Application Service for snapshots and ownership. */
public record JobSummaryResponse(UUID jobId, String title, UUID companyId, String companyName,
                                 UUID recruiterOwnerUserId, JobStatus status, Instant deadline,
                                 BigDecimal ctcLpa, EmploymentType employmentType,
                                 ScreeningMode screeningMode, BigDecimal autoShortlistMinCgpa,
                                 int autoShortlistMaxBacklogs) {
}
