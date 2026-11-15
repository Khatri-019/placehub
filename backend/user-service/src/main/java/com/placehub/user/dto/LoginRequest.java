package com.placehub.user.dto;

import com.placehub.user.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code portal} (optional, spec 20.5) names the login page used; a mismatch with the account role is rejected. */
public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password,
                           Role portal) {

    public LoginRequest(String email, String password) {
        this(email, password, null);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***, portal=" + portal + "]";
    }
}
