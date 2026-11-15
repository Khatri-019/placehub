package com.placehub.application.policy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repositories for the policy package. */
public final class PolicyRepositories {

    private PolicyRepositories() {
    }

    public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, UUID> {

        Optional<PolicyVersion> findByActiveTrue();

        Page<PolicyVersion> findAllByOrderByVersionDesc(Pageable pageable);

        Optional<PolicyVersion> findByVersion(int version);

        @Query("select coalesce(max(p.version), 0) from PolicyVersion p")
        int maxVersion();
    }

    public interface StudentPlacementStateRepository extends JpaRepository<StudentPlacementState, UUID> {
    }

    public interface PlacementRepository extends JpaRepository<Placement, UUID>, JpaSpecificationExecutor<Placement> {

        List<Placement> findByStudentUserIdOrderByRecordedAtDesc(UUID studentUserId);

        @Query("select max(p.ctcLpa) from Placement p where p.studentUserId = :id")
        BigDecimal highestCtc(@Param("id") UUID studentUserId);

        boolean existsByApplicationId(UUID applicationId);
    }

    public interface ConsentRepository extends JpaRepository<Consent, UUID>, JpaSpecificationExecutor<Consent> {

        Optional<Consent> findByApplicationId(UUID applicationId);
    }

    public interface PolicyAuditRepository extends JpaRepository<PolicyAuditEntry, UUID> {

        Page<PolicyAuditEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
    }
}
