package com.placehub.application.mapper;

import com.placehub.application.dto.ApplicationDtos.ApplicationResponse;
import com.placehub.application.entity.Application;

public final class ApplicationMapper {

    private ApplicationMapper() {
    }

    /**
     * @param includeRecruiterNote the recruiter's note is internal feedback, shown to the owning recruiter and admins
     *                             only (spec is silent; documented in IMPLEMENTATION_NOTES.md)
     */
    public static ApplicationResponse toResponse(Application a, boolean includeRecruiterNote) {
        return new ApplicationResponse(a.getId(), a.getJobId(), a.getStudentUserId(), a.getRecruiterOwnerUserId(),
                a.getJobTitleSnapshot(), a.getCompanyNameSnapshot(), a.getStudentNameSnapshot(),
                a.getStudentEmailSnapshot(), a.getBranchSnapshot(), a.getCgpaSnapshot(),
                a.getGraduationYearSnapshot(), a.getBacklogCountSnapshot(), a.getStatus(),
                includeRecruiterNote ? a.getRecruiterNote() : null, a.getAppliedAt(), a.getUpdatedAt(),
                a.getCtcLpaSnapshot(), a.getEmploymentTypeSnapshot(), a.getResumeIdSnapshot() != null,
                a.getPolicyDecisionSnapshot());
    }
}
