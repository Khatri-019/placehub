package com.placehub.user.dto;

import com.placehub.user.domain.Branch;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StudentRegistrationRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = Validation.PASSWORD_REGEX, message = Validation.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(max = 150) String collegeName,
        @NotNull Branch branch,
        @NotNull @Min(2020) @Max(2035) Integer graduationYear,
        @NotNull @DecimalMin("0.00") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2) BigDecimal cgpa,
        @NotNull @Min(0) @Max(50) Integer backlogCount) {

    @Override
    public String toString() {
        return "StudentRegistrationRequest[email=" + email + ", password=***]";
    }
}
