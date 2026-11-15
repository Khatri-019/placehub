package com.placehub.user.dto;

import com.placehub.user.domain.AccountStatus;
import com.placehub.user.domain.Role;

import java.util.UUID;

public record InternalUserResponse(UUID id, String fullName, String email, Role role, AccountStatus status) {
}
