package com.placehub.application.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Per-student lock flag. Current CTC is derived from {@link Placement} rows, not stored here. */
@Entity
@Table(name = "student_placement_states")
public class StudentPlacementState {

    @Id
    @Column(name = "student_user_id")
    private UUID studentUserId;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "lock_reason", length = 1000)
    private String lockReason;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected StudentPlacementState() {
    }

    public StudentPlacementState(UUID studentUserId, Instant now) {
        this.studentUserId = studentUserId;
        this.updatedAt = now;
    }

    public void lock(String reason, UUID by, Instant now) {
        this.locked = true;
        this.lockReason = reason;
        this.lockedAt = now;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    public void unlock(UUID by, Instant now) {
        this.locked = false;
        this.lockReason = null;
        this.lockedAt = null;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    public UUID getStudentUserId() {
        return studentUserId;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getLockReason() {
        return lockReason;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Long getVersion() {
        return version;
    }
}
