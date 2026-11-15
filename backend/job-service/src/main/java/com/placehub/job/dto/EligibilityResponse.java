package com.placehub.job.dto;

import java.util.List;
import java.util.UUID;

public record EligibilityResponse(UUID jobId, boolean eligible, List<String> reasons) {
}
