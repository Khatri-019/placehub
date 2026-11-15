package com.placehub.notification.messaging;

import java.time.Instant;
import java.util.UUID;

/** Consumer-side copies of the v1 event contracts (contracts/events/*.json). Not shared with the producer. */
public final class ApplicationEvents {

    public static final String CREATED_TOPIC = "placehub.application.created.v1";
    public static final String STATUS_CHANGED_TOPIC = "placehub.application.status-changed.v1";
    public static final String DLT_SUFFIX = ".DLT";
    public static final String CREATED = "APPLICATION_CREATED";
    public static final String STATUS_CHANGED = "APPLICATION_STATUS_CHANGED";

    private ApplicationEvents() {
    }

    public record ApplicationCreated(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                                     UUID applicationId, UUID jobId, UUID studentUserId, String studentName,
                                     UUID recruiterUserId, String jobTitle, String companyName) {
    }

    public record ApplicationStatusChanged(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                                           UUID applicationId, UUID jobId, UUID studentUserId, String studentName,
                                           UUID recruiterUserId, String jobTitle, String companyName,
                                           String oldStatus, String newStatus, UUID changedByUserId) {
    }
}
