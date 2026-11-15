package com.placehub.application.entity;

import com.placehub.application.domain.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Immutable record of one status transition (spec 20.4). {@code fromStatus} is null for creation. */
@Entity
@Immutable
@Table(name = "application_status_history")
public class StatusHistory {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ApplicationStatus toStatus;

    @Column(name = "actor_type", nullable = false, length = 10)
    private String actorType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(length = 1000)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    /** Database-assigned insertion order (BIGSERIAL). */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    protected StatusHistory() {
    }

    public StatusHistory(UUID id, UUID applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus,
                         String actorType, UUID actorUserId, String reason, Instant changedAt) {
        this.id = id;
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public String getActorType() {
        return actorType;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
