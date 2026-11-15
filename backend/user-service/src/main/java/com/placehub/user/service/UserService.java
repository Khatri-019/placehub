package com.placehub.user.service;

import com.placehub.common.error.ApiException;
import com.placehub.common.paging.PageResponse;
import com.placehub.common.paging.Paging;
import com.placehub.user.domain.AccountStatus;
import com.placehub.user.domain.Role;
import com.placehub.user.dto.AcademicProfileResponse;
import com.placehub.user.dto.InternalUserResponse;
import com.placehub.user.dto.UserResponse;
import com.placehub.user.entity.StudentDetails;
import com.placehub.user.entity.User;
import com.placehub.user.mapper.UserMapper;
import com.placehub.user.repository.RecruiterDetailsRepository;
import com.placehub.user.repository.StudentDetailsRepository;
import com.placehub.user.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt", "fullName", "fullName", "email", "email");

    private final UserRepository users;
    private final StudentDetailsRepository students;
    private final RecruiterDetailsRepository recruiters;

    public UserService(UserRepository users, StudentDetailsRepository students, RecruiterDetailsRepository recruiters) {
        this.users = users;
        this.students = students;
        this.recruiters = recruiters;
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        return UserMapper.toResponse(user, students.findById(userId).orElse(null),
                recruiters.findById(userId).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Role role, AccountStatus status, String q, Integer page, Integer size,
                                           String sort) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("accountStatus"), status));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like, '\\'),
                    cb.like(cb.lower(root.get("email")), like, '\\')));
        }
        var pageable = Paging.of(page, size, sort, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(users.findAll(spec, pageable), UserMapper::toSummary);
    }

    @Transactional
    public UserResponse updateStatus(UUID actingAdminId, UUID targetId, AccountStatus newStatus) {
        if (newStatus != AccountStatus.ACTIVE && newStatus != AccountStatus.DEACTIVATED) {
            throw ApiException.badRequest("Status must be ACTIVE or DEACTIVATED");
        }
        User user = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getId().equals(actingAdminId) && newStatus == AccountStatus.DEACTIVATED) {
            throw ApiException.conflict("You cannot deactivate your own account");
        }
        user.setAccountStatus(newStatus);
        users.saveAndFlush(user);
        return UserMapper.toSummary(user);
    }

    @Transactional(readOnly = true)
    public InternalUserResponse internalUser(UUID id) {
        return users.findById(id).map(UserMapper::toInternal)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional(readOnly = true)
    public AcademicProfileResponse academicProfile(UUID id) {
        User user = users.findById(id).filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.notFound("Student not found"));
        StudentDetails details = students.findById(id)
                .orElseThrow(() -> ApiException.notFound("Student not found"));
        return UserMapper.toAcademicProfile(user, details);
    }
}
