package com.placehub.application.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit of policy versions, locks, unlocks and off-campus records. */
@Entity
@Immutable
@Table(name = "policy_audit_log")
public class PolicyAuditEntry {

    public enum Action { POLICY_VERSION_CREATED, STUDENT_LOCKED, STUDENT_UNLOCKED, OFF_CAMPUS_RECORDED }

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, length = 40)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Action action;

    @Column(name = "student_user_id")
    private UUID studentUserId;

    @Column(name = "before_json", columnDefinition = "text")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "text")
    private String afterJson;

    @Column(length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyAuditEntry() {
    }

    public PolicyAuditEntry(UUID id, UUID actorUserId, Action action, UUID studentUserId, String beforeJson,
                            String afterJson, String reason, Instant createdAt) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.action = action;
        this.studentUserId = studentUserId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Action getAction() {
        return action;
    }

    public UUID getStudentUserId() {
        return studentUserId;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
