package com.placehub.application.repository;

import com.placehub.application.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Claims due PENDING rows; SKIP LOCKED lets several relay instances run without double-sending. */
    @Query(value = """
            SELECT * FROM outbox_events
             WHERE status = 'PENDING' AND next_attempt_at <= :now
             ORDER BY created_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<OutboxEvent> lockDueBatch(@Param("now") Instant now, @Param("limit") int limit);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAt(UUID aggregateId);
}
