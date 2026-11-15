package com.placehub.application.repository;

import com.placehub.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

    boolean existsByJobIdAndStudentUserId(UUID jobId, UUID studentUserId);

    List<Application> findByStudentUserId(UUID studentUserId);

    /** Non-terminal applications that went through the consent band (for maxActiveConsents). */
    @Query("select count(a) from Application a where a.studentUserId = :id and a.policyDecisionSnapshot = 'REQUIRE_CONSENT'"
            + " and a.status in (com.placehub.application.domain.ApplicationStatus.APPLIED,"
            + " com.placehub.application.domain.ApplicationStatus.UNDER_REVIEW,"
            + " com.placehub.application.domain.ApplicationStatus.SHORTLISTED)")
    long countActiveConsentApplications(@Param("id") UUID studentId);

    /** Rows of [status, count]. */
    @Query("select a.status, count(a) from Application a where a.studentUserId = :id group by a.status")
    List<Object[]> countByStatusForStudent(@Param("id") UUID studentId);

    @Query("select a.status, count(a) from Application a where a.recruiterOwnerUserId = :id group by a.status")
    List<Object[]> countByStatusForRecruiter(@Param("id") UUID recruiterId);
}
