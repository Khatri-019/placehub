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

    /** Rows of [status, count]. */
    @Query("select a.status, count(a) from Application a where a.studentUserId = :id group by a.status")
    List<Object[]> countByStatusForStudent(@Param("id") UUID studentId);

    @Query("select a.status, count(a) from Application a where a.recruiterOwnerUserId = :id group by a.status")
    List<Object[]> countByStatusForRecruiter(@Param("id") UUID recruiterId);
}
