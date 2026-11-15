package com.placehub.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code placehub.recruiter.auto-approve}: when true new recruiters are ACTIVE, otherwise PENDING_APPROVAL. */
@ConfigurationProperties(prefix = "placehub.recruiter")
public record RecruiterProperties(Boolean autoApprove) {

    public RecruiterProperties {
        autoApprove = autoApprove == null || autoApprove;
    }
}
