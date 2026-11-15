package com.placehub.user.service;

import com.placehub.common.error.ApiException;
import com.placehub.user.config.RecruiterProperties;
import com.placehub.user.domain.AccountStatus;
import com.placehub.user.domain.Role;
import com.placehub.user.dto.LoginRequest;
import com.placehub.user.dto.LoginResponse;
import com.placehub.user.dto.RecruiterRegistrationRequest;
import com.placehub.user.dto.StudentRegistrationRequest;
import com.placehub.user.dto.UserResponse;
import com.placehub.user.entity.RecruiterDetails;
import com.placehub.user.entity.StudentDetails;
import com.placehub.user.entity.User;
import com.placehub.user.mapper.UserMapper;
import com.placehub.user.repository.RecruiterDetailsRepository;
import com.placehub.user.repository.StudentDetailsRepository;
import com.placehub.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

/** Registration and login. Public registration can never create an ADMIN. */
@Service
public class AuthService {

    static final String DUPLICATE_EMAIL = "An account with this email already exists";
    static final String BAD_CREDENTIALS = "Invalid email or password";
    static final String DEACTIVATED = "This account has been deactivated. Contact the placement office.";
    static final String PENDING = "This account is awaiting administrator approval.";

    private final UserRepository users;
    private final StudentDetailsRepository students;
    private final RecruiterDetailsRepository recruiters;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RecruiterProperties props;
    /** Hash used to keep login timing similar for unknown emails. */
    private final String dummyHash;
    private final LoginThrottle throttle;

    public AuthService(UserRepository users, StudentDetailsRepository students, RecruiterDetailsRepository recruiters,
                       PasswordEncoder passwordEncoder, TokenService tokenService, RecruiterProperties props) {
        this(users, students, recruiters, passwordEncoder, tokenService, props,
                new LoginThrottle(java.time.Clock.systemUTC(), LoginThrottle.DEFAULT_MAX_FAILURES, LoginThrottle.DEFAULT_WINDOW));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(UserRepository users, StudentDetailsRepository students, RecruiterDetailsRepository recruiters,
                       PasswordEncoder passwordEncoder, TokenService tokenService, RecruiterProperties props,
                       LoginThrottle throttle) {
        this.throttle = throttle;
        this.users = users;
        this.students = students;
        this.recruiters = recruiters;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.props = props;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-" + UUID.randomUUID());
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserResponse registerStudent(StudentRegistrationRequest req) {
        String email = normalizeEmail(req.email());
        ensureEmailFree(email);
        User user = new User(UUID.randomUUID(), req.fullName().trim(), email,
                passwordEncoder.encode(req.password()), Role.STUDENT, AccountStatus.ACTIVE);
        saveUser(user);
        StudentDetails details = students.save(new StudentDetails(user.getId(), req.collegeName().trim(),
                req.branch(), req.graduationYear(), req.cgpa().setScale(2, RoundingMode.HALF_UP),
                req.backlogCount()));
        return UserMapper.toResponse(user, details, null);
    }

    @Transactional
    public UserResponse registerRecruiter(RecruiterRegistrationRequest req) {
        String email = normalizeEmail(req.email());
        ensureEmailFree(email);
        AccountStatus status = props.autoApprove() ? AccountStatus.ACTIVE : AccountStatus.PENDING_APPROVAL;
        User user = new User(UUID.randomUUID(), req.fullName().trim(), email,
                passwordEncoder.encode(req.password()), Role.RECRUITER, status);
        saveUser(user);
        RecruiterDetails details = recruiters.save(new RecruiterDetails(user.getId(), req.companyName().trim(),
                req.designation().trim()));
        return UserMapper.toResponse(user, null, details);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        throttle.checkAllowed(email);
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throttle.recordFailure(email);
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throttle.recordFailure(email);
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        throttle.recordSuccess(email);
        // Only reached with valid credentials, so the role is never revealed to someone without the password.
        if (req.portal() != null && req.portal() != user.getRole()) {
            throw ApiException.forbidden(portalMismatch(req.portal(), user.getRole()));
        }
        switch (user.getAccountStatus()) {
            case DEACTIVATED -> throw ApiException.forbidden(DEACTIVATED);
            case PENDING_APPROVAL -> throw ApiException.forbidden(PENDING);
            case ACTIVE -> { }
        }
        TokenService.IssuedToken token = tokenService.issue(user);
        UserResponse dto = UserMapper.toResponse(user, students.findById(user.getId()).orElse(null),
                recruiters.findById(user.getId()).orElse(null));
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), dto);
    }

    static String portalMismatch(Role portal, Role actual) {
        return "This account is not registered as a " + portalName(portal) + ". Use the " + portalName(actual)
                + " login.";
    }

    private static String portalName(Role role) {
        return switch (role) {
            case STUDENT -> "student";
            case RECRUITER -> "recruiter";
            case ADMIN -> "placement office";
        };
    }

    private void ensureEmailFree(String email) {
        if (users.existsByEmail(email)) {
            throw ApiException.conflict(DUPLICATE_EMAIL);
        }
    }

    private void saveUser(User user) {
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(DUPLICATE_EMAIL);
        }
    }
}
