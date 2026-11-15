package com.placehub.user.repository;

import com.placehub.user.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    /** Metadata-only projection: never loads the PDF bytes. */
    interface ResumeSummary {
        UUID getId();

        String getOriginalFilename();

        int getSizeBytes();

        String getSha256();

        Instant getUploadedAt();

        boolean isCurrent();

        boolean isReferenced();
    }

    List<ResumeSummary> findByUserIdOrderByUploadedAtDesc(UUID userId);

    Optional<ResumeSummary> findFirstByUserIdAndCurrentTrue(UUID userId);

    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update Resume r set r.current = false where r.userId = :userId and r.current = true")
    int clearCurrent(@Param("userId") UUID userId);

    @Modifying
    @Query("update Resume r set r.referenced = true where r.id = :id and r.userId = :userId")
    int markReferenced(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from Resume r where r.id in :ids and r.referenced = false and r.current = false")
    int deletePrunable(@Param("ids") List<UUID> ids);
}
