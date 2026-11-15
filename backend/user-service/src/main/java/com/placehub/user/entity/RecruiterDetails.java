package com.placehub.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "recruiter_details")
public class RecruiterDetails {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(nullable = false, length = 100)
    private String designation;

    protected RecruiterDetails() {
    }

    public RecruiterDetails(UUID userId, String companyName, String designation) {
        this.userId = userId;
        this.companyName = companyName;
        this.designation = designation;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getDesignation() {
        return designation;
    }
}
