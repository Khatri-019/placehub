package com.placehub.user.mapper;

import com.placehub.user.dto.AcademicProfileResponse;
import com.placehub.user.dto.InternalUserResponse;
import com.placehub.user.dto.UserResponse;
import com.placehub.user.entity.RecruiterDetails;
import com.placehub.user.entity.StudentDetails;
import com.placehub.user.entity.User;

/** Hand-written entity to DTO mapping. Password hashes never leave this service. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user, StudentDetails student, RecruiterDetails recruiter) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.getAccountStatus(), user.getCreatedAt(),
                student == null ? null : new UserResponse.StudentDetailsDto(student.getCollegeName(),
                        student.getBranch(), student.getGraduationYear(), student.getCgpa(),
                        student.getBacklogCount()),
                recruiter == null ? null : new UserResponse.RecruiterDetailsDto(recruiter.getCompanyName(),
                        recruiter.getDesignation()));
    }

    public static UserResponse toSummary(User user) {
        return toResponse(user, null, null);
    }

    public static InternalUserResponse toInternal(User user) {
        return new InternalUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.getAccountStatus());
    }

    public static AcademicProfileResponse toAcademicProfile(User user, StudentDetails s) {
        return new AcademicProfileResponse(user.getId(), user.getFullName(), user.getEmail(), s.getBranch(),
                s.getGraduationYear(), s.getCgpa(), s.getBacklogCount(), user.getAccountStatus());
    }
}
