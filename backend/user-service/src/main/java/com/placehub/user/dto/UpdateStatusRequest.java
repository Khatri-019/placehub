package com.placehub.user.dto;

import com.placehub.user.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull AccountStatus status) {
}
