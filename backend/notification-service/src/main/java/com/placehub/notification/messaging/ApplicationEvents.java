package com.placehub.notification.messaging;

import java.time.Instant;
import java.util.UUID;

/** Consumer-side copies of the v1 event contracts (contracts/events/*.json). Not shared with the producer. */
public final class ApplicationEvents {

    public static final String CREATED_TOPIC = "placehub.application.created.v1";
    public static final String STATUS_CHANGED_TOPIC = "placehub.application.status-changed.v1";
    public static final String PLACEMENT_TOPIC = "placehub.placement.recorded.v1";
    public static final String PLACEMENT_RECORDED = "STUDENT_PLACEMENT_RECORDED";
    public static final String DLT_SUFFIX = ".DLT";
    public static final String CREATED = "APPLICATION_CREATED";
    public static final String STATUS_CHANGED = "APPLICATION_STATUS_CHANGED";

    private ApplicationEvents() {
    }

    /** {@code autoShortlisted} is an optional v3 field (null on older events). */
    public record ApplicationCreated(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                                     UUID applicationId, UUID jobId, UUID studentUserId, String studentName,
                                     UUID recruiterUserId, String jobTitle, String companyName,
                                     Boolean autoShortlisted) {

        public boolean wasAutoShortlisted() {
            return Boolean.TRUE.equals(autoShortlisted);
        }
    }

    public record ApplicationStatusChanged(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                                           UUID applicationId, UUID jobId, UUID studentUserId, String studentName,
                                           UUID recruiterUserId, String jobTitle, String companyName,
                                           String oldStatus, String newStatus, UUID changedByUserId,
                                           String actorType, String reason) {

        /** Optional v3 field; events without it were produced by a user. */
        public boolean bySystem() {
            return "SYSTEM".equals(actorType);
        }
    }

    public record StudentPlacementRecorded(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                                           UUID placementId, UUID studentUserId, String studentName,
                                           UUID applicationId, String companyName, java.math.BigDecimal ctcLpa,
                                           boolean locked, String source) {
    }
}
