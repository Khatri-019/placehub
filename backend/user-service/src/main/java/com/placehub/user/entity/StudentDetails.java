package com.placehub.user.entity;

import com.placehub.user.domain.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "student_details")
public class StudentDetails {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "college_name", nullable = false, length = 150)
    private String collegeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Branch branch;

    @Column(name = "graduation_year", nullable = false)
    private int graduationYear;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(name = "backlog_count", nullable = false)
    private int backlogCount;

    protected StudentDetails() {
    }

    public StudentDetails(UUID userId, String collegeName, Branch branch, int graduationYear, BigDecimal cgpa,
                          int backlogCount) {
        this.userId = userId;
        this.collegeName = collegeName;
        this.branch = branch;
        this.graduationYear = graduationYear;
        this.cgpa = cgpa;
        this.backlogCount = backlogCount;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCollegeName() {
        return collegeName;
    }

    public Branch getBranch() {
        return branch;
    }

    public int getGraduationYear() {
        return graduationYear;
    }

    public BigDecimal getCgpa() {
        return cgpa;
    }

    public int getBacklogCount() {
        return backlogCount;
    }
}
