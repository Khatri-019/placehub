package com.placehub.application.mapper;

import com.placehub.application.dto.ApplicationDtos.ApplicationResponse;
import com.placehub.application.entity.Application;

public final class ApplicationMapper {

    private ApplicationMapper() {
    }

    /**
     * @param includeRecruiterNote staff view (owning recruiter or admin). The recruiter's note is internal feedback
     *                             (spec is silent; IMPLEMENTATION_NOTES.md), and students do not get the recruiter's
     *                             user id either (security review F6)
     */
    public static ApplicationResponse toResponse(Application a, boolean includeRecruiterNote) {
        return new ApplicationResponse(a.getId(), a.getJobId(), a.getStudentUserId(),
                includeRecruiterNote ? a.getRecruiterOwnerUserId() : null,
                a.getJobTitleSnapshot(), a.getCompanyNameSnapshot(), a.getStudentNameSnapshot(),
                a.getStudentEmailSnapshot(), a.getBranchSnapshot(), a.getCgpaSnapshot(),
                a.getGraduationYearSnapshot(), a.getBacklogCountSnapshot(), a.getStatus(),
                includeRecruiterNote ? a.getRecruiterNote() : null, a.getAppliedAt(), a.getUpdatedAt(),
                a.getCtcLpaSnapshot(), a.getEmploymentTypeSnapshot(), a.getResumeIdSnapshot() != null,
                a.getPolicyDecisionSnapshot(), a.isAutoShortlisted());
    }
}
