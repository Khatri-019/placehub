package com.placehub.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record ResumeMetadata(UUID id, String filename, int sizeBytes, String sha256, Instant uploadedAt,
                                 boolean current, boolean referenced) {
    }

    public record ResumeOverview(ResumeMetadata current, List<ResumeMetadata> versions) {
    }
}
