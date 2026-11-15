package com.placehub.application.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A stored policy version. Versions are append-only; only {@code active} ever changes (spec 20.3). */
@Entity
@Table(name = "placement_policies")
public class PolicyVersion {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private int version;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "bands_json", nullable = false, columnDefinition = "text")
    private String bandsJson;

    @Column(name = "applies_to_internships", nullable = false)
    private boolean appliesToInternships;

    @Column(name = "max_active_consents")
    private Integer maxActiveConsents;

    @Column(name = "resume_required", nullable = false)
    private boolean resumeRequired;

    @Column(name = "withdraw_others_on_lock", nullable = false)
    private boolean withdrawOthersOnLock;

    @Column(name = "consent_text", nullable = false, columnDefinition = "text")
    private String consentText;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "change_note", nullable = false, length = 500)
    private String changeNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyVersion() {
    }

    public PolicyVersion(UUID id, int version, String bandsJson, boolean appliesToInternships,
                         Integer maxActiveConsents, boolean resumeRequired, boolean withdrawOthersOnLock,
                         String consentText, Instant effectiveFrom, UUID changedByUserId, String changeNote,
                         Instant createdAt) {
        this.id = id;
        this.version = version;
        this.active = true;
        this.bandsJson = bandsJson;
        this.appliesToInternships = appliesToInternships;
        this.maxActiveConsents = maxActiveConsents;
        this.resumeRequired = resumeRequired;
        this.withdrawOthersOnLock = withdrawOthersOnLock;
        this.consentText = consentText;
        this.effectiveFrom = effectiveFrom;
        this.changedByUserId = changedByUserId;
        this.changeNote = changeNote;
        this.createdAt = createdAt;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return active;
    }

    public String getBandsJson() {
        return bandsJson;
    }

    public boolean isAppliesToInternships() {
        return appliesToInternships;
    }

    public Integer getMaxActiveConsents() {
        return maxActiveConsents;
    }

    public boolean isResumeRequired() {
        return resumeRequired;
    }

    public boolean isWithdrawOthersOnLock() {
        return withdrawOthersOnLock;
    }

    public String getConsentText() {
        return consentText;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public UUID getChangedByUserId() {
        return changedByUserId;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
