package com.placehub.job.dto;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(UUID id, String name, String website, String description, String industry,
                              UUID recruiterOwnerUserId, Instant createdAt, Instant updatedAt) {
}
