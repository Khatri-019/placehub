package com.placehub.job.mapper;

import com.placehub.job.dto.CompanyResponse;
import com.placehub.job.dto.JobResponse;
import com.placehub.job.dto.JobSummaryResponse;
import com.placehub.job.entity.Company;
import com.placehub.job.entity.Job;

/** Hand-written mappers; must be called inside a transaction (lazy company and collections). */
public final class JobMapper {

    private JobMapper() {
    }

    public static CompanyResponse toResponse(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getWebsite(), c.getDescription(), c.getIndustry(),
                c.getRecruiterOwnerUserId(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public static JobResponse toResponse(Job j) {
        return new JobResponse(j.getId(), j.getCompany().getId(), j.getCompany().getName(),
                j.getRecruiterOwnerUserId(), j.getTitle(), j.getDescription(), j.getLocation(),
                j.getEmploymentType(), j.getCtc(), j.getMinCgpa(), j.getMaxBacklogs(),
                j.getEligibleBranches().stream().sorted().toList(),
                j.getEligibleGraduationYears().stream().sorted().toList(),
                j.getDeadline(), j.getStatus(), j.getCreatedAt(), j.getUpdatedAt());
    }

    public static JobSummaryResponse toSummary(Job j) {
        return new JobSummaryResponse(j.getId(), j.getTitle(), j.getCompany().getId(), j.getCompany().getName(),
                j.getRecruiterOwnerUserId(), j.getStatus(), j.getDeadline());
    }
}
