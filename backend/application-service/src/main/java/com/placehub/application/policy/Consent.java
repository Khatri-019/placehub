package com.placehub.application.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Immutable consent record, written in the same transaction as its application (spec 20.3). */
@Entity
@Immutable
@Table(name = "consents")
public class Consent {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Column(name = "student_user_id", nullable = false)
    private UUID studentUserId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Column(nullable = false, length = 120)
    private String band;

    @Column(name = "consent_text_hash", nullable = false, length = 64)
    private String consentTextHash;

    @Column(name = "rendered_text", nullable = false, columnDefinition = "text")
    private String renderedText;

    @Column(name = "signed_name", nullable = false, length = 100)
    private String signedName;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected Consent() {
    }

    public Consent(UUID id, UUID applicationId, UUID studentUserId, UUID jobId, int policyVersion, String band,
                   String consentTextHash, String renderedText, String signedName, Instant acceptedAt,
                   String clientIp, String userAgent) {
        this.id = id;
        this.applicationId = applicationId;
        this.studentUserId = studentUserId;
        this.jobId = jobId;
        this.policyVersion = policyVersion;
        this.band = band;
        this.consentTextHash = consentTextHash;
        this.renderedText = renderedText;
        this.signedName = signedName;
        this.acceptedAt = acceptedAt;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getStudentUserId() {
        return studentUserId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getPolicyVersion() {
        return policyVersion;
    }

    public String getBand() {
        return band;
    }

    public String getConsentTextHash() {
        return consentTextHash;
    }

    public String getRenderedText() {
        return renderedText;
    }

    public String getSignedName() {
        return signedName;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
