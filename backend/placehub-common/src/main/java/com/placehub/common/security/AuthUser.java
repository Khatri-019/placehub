package com.placehub.common.security;

import com.placehub.common.error.ApiException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Identity derived exclusively from a validated JWT (never from request bodies or custom headers). */
public record AuthUser(UUID id, String role, String email, String name) {

    public static final String STUDENT = "STUDENT";
    public static final String RECRUITER = "RECRUITER";
    public static final String ADMIN = "ADMIN";

    public static AuthUser from(Jwt jwt) {
        if (jwt == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        try {
            return new AuthUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"),
                    jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.unauthorized("Token subject is not a valid user id");
        }
    }

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }

    public boolean isStudent() {
        return STUDENT.equals(role);
    }

    public boolean isRecruiter() {
        return RECRUITER.equals(role);
    }
}
