package com.placehub.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecruiterRegistrationRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = Validation.PASSWORD_REGEX, message = Validation.PASSWORD_MESSAGE) String password,
        @NotBlank @Size(max = 150) String companyName,
        @NotBlank @Size(max = 100) String designation) {

    @Override
    public String toString() {
        return "RecruiterRegistrationRequest[email=" + email + ", password=***]";
    }
}
