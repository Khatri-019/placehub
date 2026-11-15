package com.placehub.application.repository;

import com.placehub.application.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {

    @Query("select h from StatusHistory h where h.applicationId = :id order by h.seq asc")
    List<StatusHistory> timeline(@Param("id") UUID applicationId);
}
