package com.placehub.application.messaging;

import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.entity.Application;
import com.placehub.application.entity.OutboxEvent;
import com.placehub.application.repository.OutboxRepository;
import com.placehub.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes outbox rows. MANDATORY propagation guarantees each row joins the caller's transaction, so the event exists
 * if and only if the application change committed.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public OutboxWriter(OutboxRepository outbox, JsonMapper jsonMapper, Clock clock) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent applicationCreated(Application a) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        var event = new ApplicationEvents.ApplicationCreated(eventId, ApplicationEvents.CREATED,
                ApplicationEvents.VERSION, now, a.getId(), a.getJobId(), a.getStudentUserId(),
                a.getStudentNameSnapshot(), a.getRecruiterOwnerUserId(), a.getJobTitleSnapshot(),
                a.getCompanyNameSnapshot());
        return save(eventId, a.getId(), ApplicationEvents.CREATED, ApplicationEvents.CREATED_TOPIC, event, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent statusChanged(Application a, ApplicationStatus oldStatus, UUID changedBy) {
        return statusChanged(a, oldStatus, changedBy, "USER", null);
    }

    /** {@code actorType} USER or SYSTEM (additive v3 field); SYSTEM changes carry a null changedByUserId. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent statusChanged(Application a, ApplicationStatus oldStatus, UUID changedBy, String actorType,
                                     String reason) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        var event = new ApplicationEvents.ApplicationStatusChanged(eventId, ApplicationEvents.STATUS_CHANGED,
                ApplicationEvents.VERSION, now, a.getId(), a.getJobId(), a.getStudentUserId(),
                a.getStudentNameSnapshot(), a.getRecruiterOwnerUserId(), a.getJobTitleSnapshot(),
                a.getCompanyNameSnapshot(), oldStatus.name(), a.getStatus().name(),
                "SYSTEM".equals(actorType) ? null : changedBy, actorType, reason);
        return save(eventId, a.getId(), ApplicationEvents.STATUS_CHANGED, ApplicationEvents.STATUS_CHANGED_TOPIC,
                event, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent placementRecorded(com.placehub.application.policy.Placement p) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        var event = new ApplicationEvents.StudentPlacementRecorded(eventId, ApplicationEvents.PLACEMENT_RECORDED,
                ApplicationEvents.VERSION, now, p.getId(), p.getStudentUserId(), p.getStudentName(),
                p.getApplicationId(), p.getCompanyName(), p.getCtcLpa(), p.isLocked(), p.getSource().name());
        return save(eventId, p.getStudentUserId(), ApplicationEvents.PLACEMENT_RECORDED, ApplicationEvents.PLACEMENT_TOPIC,
                event, now);
    }

    private OutboxEvent save(UUID eventId, UUID aggregateId, String type, String topic, Object event, Instant now) {
        String payload = jsonMapper.writeValueAsString(event);
        return outbox.save(new OutboxEvent(eventId, aggregateId, type, topic, payload,
                MDC.get(CorrelationIdFilter.MDC_KEY), now));
    }
}
