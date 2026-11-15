package com.placehub.user.dto;

import com.placehub.user.domain.Branch;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationValidationTest {

    static jakarta.validation.ValidatorFactory factory;
    static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    static StudentRegistrationRequest valid() {
        return new StudentRegistrationRequest("Asha Rao", "asha@example.com", "Secret123", "PIT", Branch.CSE,
                2027, new BigDecimal("8.40"), 0);
    }

    Set<String> invalidFields(Object request) {
        Set<ConstraintViolation<Object>> v = validator.validate(request);
        return v.stream().map(c -> c.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void validStudentPasses() {
        assertThat(invalidFields(valid())).isEmpty();
    }

    @Test
    void rejectsEachInvalidStudentField() {
        assertThat(invalidFields(new StudentRegistrationRequest("A", "not-an-email", "short", "", null, 2019,
                new BigDecimal("10.50"), 51)))
                .containsExactlyInAnyOrder("fullName", "email", "password", "collegeName", "branch",
                        "graduationYear", "cgpa", "backlogCount");
    }

    @Test
    void passwordNeedsLetterAndDigit() {
        var lettersOnly = new StudentRegistrationRequest("Asha", "a@b.com", "abcdefghij", "PIT", Branch.ME, 2027,
                new BigDecimal("7"), 0);
        var digitsOnly = new StudentRegistrationRequest("Asha", "a@b.com", "1234567890", "PIT", Branch.ME, 2027,
                new BigDecimal("7"), 0);
        assertThat(invalidFields(lettersOnly)).containsExactly("password");
        assertThat(invalidFields(digitsOnly)).containsExactly("password");
    }

    @Test
    void cgpaAllowsAtMostTwoDecimals() {
        var r = new StudentRegistrationRequest("Asha", "a@b.com", "Secret123", "PIT", Branch.ME, 2027,
                new BigDecimal("8.456"), 0);
        assertThat(invalidFields(r)).containsExactly("cgpa");
    }

    @Test
    void recruiterRequiresCompanyAndDesignation() {
        assertThat(invalidFields(new RecruiterRegistrationRequest("Riya", "r@corp.com", "Secret123", " ", "")))
                .containsExactlyInAnyOrder("companyName", "designation");
    }

    @Test
    void toStringNeverLeaksPassword() {
        assertThat(valid().toString()).doesNotContain("Secret123");
        assertThat(new LoginRequest("a@b.com", "Secret123").toString()).doesNotContain("Secret123");
    }
}
