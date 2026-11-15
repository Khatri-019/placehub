package com.placehub.application.service;

import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.entity.Application;
import com.placehub.application.entity.StatusHistory;
import com.placehub.application.repository.StatusHistoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** One history row per status transition, in the transaction that changes the status (spec 20.4). */
@Component
public class StatusHistoryWriter {

    public enum ActorType { USER, SYSTEM }

    private final StatusHistoryRepository history;
    private final Clock clock;

    public StatusHistoryWriter(StatusHistoryRepository history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Application application, ApplicationStatus from, ApplicationStatus to, ActorType actorType,
                       UUID actorUserId, String reason) {
        history.save(new StatusHistory(UUID.randomUUID(), application.getId(), from, to, actorType.name(),
                actorType == ActorType.SYSTEM ? null : actorUserId, reason, clock.instant()));
    }
}
