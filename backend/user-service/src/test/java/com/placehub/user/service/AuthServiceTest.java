package com.placehub.user.service;

import com.placehub.common.error.ApiException;
import com.placehub.common.security.PlaceHubSecurityProperties;
import com.placehub.user.config.JwtProperties;
import com.placehub.user.config.RecruiterProperties;
import com.placehub.user.domain.AccountStatus;
import com.placehub.user.domain.Branch;
import com.placehub.user.domain.Role;
import com.placehub.user.dto.LoginRequest;
import com.placehub.user.dto.LoginResponse;
import com.placehub.user.dto.RecruiterRegistrationRequest;
import com.placehub.user.dto.StudentRegistrationRequest;
import com.placehub.user.dto.UserResponse;
import com.placehub.user.entity.User;
import com.placehub.user.repository.RecruiterDetailsRepository;
import com.placehub.user.repository.StudentDetailsRepository;
import com.placehub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes!";

    UserRepository users = mock(UserRepository.class);
    StudentDetailsRepository students = mock(StudentDetailsRepository.class);
    RecruiterDetailsRepository recruiters = mock(RecruiterDetailsRepository.class);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    TokenService tokens;
    AuthService service;

    @BeforeEach
    void setUp() {
        PlaceHubSecurityProperties sec = new PlaceHubSecurityProperties(SECRET, null, "x", List.of());
        tokens = new TokenService(NimbusJwtEncoder.withSecretKey(
                com.placehub.common.security.JwtKeys.hmacKey(SECRET)).build(), Clock.systemUTC(),
                new JwtProperties(60), sec);
        service = new AuthService(users, students, recruiters, encoder, tokens, new RecruiterProperties(true));
        when(students.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recruiters.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    static StudentRegistrationRequest student(String email) {
        return new StudentRegistrationRequest("Asha Rao", email, "Secret123", "PIT", Branch.CSE, 2027,
                new BigDecimal("8.4"), 0);
    }

    @Test
    void registersStudentWithLowercasedEmailAndBcryptHash() {
        when(users.existsByEmail("asha@example.com")).thenReturn(false);
        UserResponse res = service.registerStudent(student("  Asha@Example.COM "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail()).isEqualTo("asha@example.com");
        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        assertThat(user.getPasswordHash()).startsWith("$2a$10$").isNotEqualTo("Secret123");
        assertThat(encoder.matches("Secret123", user.getPasswordHash())).isTrue();
        assertThat(res.studentDetails().cgpa()).isEqualByComparingTo("8.40");
        assertThat(res.studentDetails().cgpa().scale()).isEqualTo(2);
        assertThat(res.toString()).doesNotContain("$2a$");
    }

    @Test
    void duplicateEmailIsConflict() {
        when(users.existsByEmail("asha@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.registerStudent(student("asha@example.com")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicateEmailRaceIsConflict() {
        when(users.existsByEmail(any())).thenReturn(false);
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_users_email"));
        assertThatThrownBy(() -> service.registerStudent(student("asha@example.com")))
                .isInstanceOf(ApiException.class)
                .hasMessage(AuthService.DUPLICATE_EMAIL);
    }

    @Test
    void recruiterStatusFollowsAutoApproveFlag() {
        var req = new RecruiterRegistrationRequest("R", "r@corp.com", "Secret123", "Acme", "HR");
        assertThat(service.registerRecruiter(req).status()).isEqualTo(AccountStatus.ACTIVE);

        AuthService manual = new AuthService(users, students, recruiters, encoder, tokens,
                new RecruiterProperties(false));
        UserResponse res = manual.registerRecruiter(new RecruiterRegistrationRequest("R", "r2@corp.com",
                "Secret123", "Acme", "HR"));
        assertThat(res.status()).isEqualTo(AccountStatus.PENDING_APPROVAL);
        assertThat(res.role()).isEqualTo(Role.RECRUITER);
        assertThat(res.recruiterDetails().companyName()).isEqualTo("Acme");
    }

    private User existing(AccountStatus status) {
        User u = new User(UUID.randomUUID(), "Asha", "asha@example.com", encoder.encode("Secret123"),
                Role.STUDENT, status);
        when(users.findByEmail("asha@example.com")).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void loginSucceedsWithBearerToken() {
        User u = existing(AccountStatus.ACTIVE);
        LoginResponse res = service.login(new LoginRequest("ASHA@example.com", "Secret123"));
        assertThat(res.tokenType()).isEqualTo("Bearer");
        assertThat(res.expiresIn()).isEqualTo(3600);
        assertThat(res.accessToken()).isNotBlank();
        assertThat(res.user().id()).isEqualTo(u.getId());
    }

    @Test
    void wrongPasswordAndUnknownEmailShareTheSame401() {
        existing(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Wrong1234")))
                .isInstanceOf(ApiException.class).hasMessage(AuthService.BAD_CREDENTIALS)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "Secret123")))
                .isInstanceOf(ApiException.class).hasMessage(AuthService.BAD_CREDENTIALS);
    }

    @Test
    void deactivatedAndPendingAccountsCannotLogIn() {
        existing(AccountStatus.DEACTIVATED);
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Secret123")))
                .isInstanceOf(ApiException.class).hasMessage(AuthService.DEACTIVATED)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        existing(AccountStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Secret123")))
                .isInstanceOf(ApiException.class).hasMessage(AuthService.PENDING);
    }

    @Test
    void portalMismatchWithCorrectPasswordIs403NamingTheRightLogin() {
        existing(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Secret123", Role.RECRUITER)))
                .isInstanceOf(ApiException.class)
                .hasMessage("This account is not registered as a recruiter. Use the student login.")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Secret123", Role.ADMIN)))
                .hasMessage("This account is not registered as a placement office. Use the student login.");
        assertThat(service.login(new LoginRequest("asha@example.com", "Secret123", Role.STUDENT)).tokenType())
                .isEqualTo("Bearer");
    }

    @Test
    void portalMismatchWithWrongPasswordIsStillTheGeneric401() {
        existing(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Wrong1234", Role.RECRUITER)))
                .hasMessage(AuthService.BAD_CREDENTIALS)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "Secret123", Role.ADMIN)))
                .hasMessage(AuthService.BAD_CREDENTIALS);
    }

    @Test
    void deactivatedLoginWithWrongPasswordDoesNotRevealStatus() {
        existing(AccountStatus.DEACTIVATED);
        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "Wrong1234")))
                .hasMessage(AuthService.BAD_CREDENTIALS);
    }
}
