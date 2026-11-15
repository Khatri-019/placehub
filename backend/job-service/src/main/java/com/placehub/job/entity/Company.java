package com.placehub.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One company per recruiter in v1 (unique recruiter_owner_user_id). UUIDs stored as CHAR(36). */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @Column(length = 36)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String website;

    @Column(length = 2000)
    private String description;

    @Column(length = 100)
    private String industry;

    @Column(name = "recruiter_owner_user_id", nullable = false, unique = true, length = 36)
    private UUID recruiterOwnerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Company() {
    }

    public Company(UUID id, UUID recruiterOwnerUserId) {
        this.id = id;
        this.recruiterOwnerUserId = recruiterOwnerUserId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(String name, String website, String description, String industry) {
        this.name = name;
        this.website = website;
        this.description = description;
        this.industry = industry;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWebsite() {
        return website;
    }

    public String getDescription() {
        return description;
    }

    public String getIndustry() {
        return industry;
    }

    public UUID getRecruiterOwnerUserId() {
        return recruiterOwnerUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
