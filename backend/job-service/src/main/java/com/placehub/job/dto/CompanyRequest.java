package com.placehub.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) @Pattern(regexp = "^(https?://.+)?$", message = "must start with http:// or https://")
        String website,
        @Size(max = 2000) String description,
        @Size(max = 100) String industry) {
}
