package com.placehub.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One stored resume version. The bytes live in Postgres (bytea); see spec 20.2 for the S3 hardening note. */
@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "original_filename", nullable = false, length = 120)
    private String originalFilename;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(nullable = false)
    private boolean referenced;

    protected Resume() {
    }

    public Resume(UUID id, UUID userId, String originalFilename, byte[] content, String sha256, Instant uploadedAt) {
        this.id = id;
        this.userId = userId;
        this.originalFilename = originalFilename;
        this.content = content;
        this.sizeBytes = content.length;
        this.sha256 = sha256;
        this.uploadedAt = uploadedAt;
        this.current = true;
    }

    public void markCurrent() {
        this.current = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public boolean isCurrent() {
        return current;
    }

    public boolean isReferenced() {
        return referenced;
    }
}
