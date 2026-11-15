package com.placehub.application.service;

import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.entity.Application;
import com.placehub.application.messaging.OutboxWriter;
import com.placehub.application.policy.Consent;
import com.placehub.application.policy.PlacementService;
import com.placehub.application.policy.PolicyRepositories.ConsentRepository;
import com.placehub.application.repository.ApplicationRepository;
import com.placehub.application.service.StatusHistoryWriter.ActorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The only place that mutates applications: each change, its history row, its outbox event(s) and any consent or
 * placement record commit in one transaction.
 */
@Component
public class ApplicationWriter {

    private final ApplicationRepository applications;
    private final OutboxWriter outbox;
    private final StatusHistoryWriter history;
    private final ConsentRepository consents;
    private final PlacementService placements;

    public ApplicationWriter(ApplicationRepository applications, OutboxWriter outbox, StatusHistoryWriter history,
                             ConsentRepository consents, PlacementService placements) {
        this.applications = applications;
        this.outbox = outbox;
        this.history = history;
        this.consents = consents;
        this.placements = placements;
    }

    /** Throws DataIntegrityViolationException on the (job_id, student_user_id) unique constraint. */
    @Transactional
    public Application create(Application application) {
        return create(application, null);
    }

    /**
     * Creates the application, its creation history row and APPLICATION_CREATED event, and (when given) the immutable
     * consent record built for it.
     */
    @Transactional
    public Application create(Application application, ConsentFactory consent) {
        return create(application, consent, false);
    }

    /**
     * When {@code autoShortlist} is true the application is created as APPLIED and moved to SHORTLISTED as SYSTEM in
     * the same transaction, writing APPLICATION_CREATED then APPLICATION_STATUS_CHANGED (spec 20.4).
     */
    @Transactional
    public Application create(Application application, ConsentFactory consent, boolean autoShortlist) {
        Application saved = applications.saveAndFlush(application);
        history.record(saved, null, ApplicationStatus.APPLIED, ActorType.USER, saved.getStudentUserId(), null);
        outbox.applicationCreated(saved, autoShortlist);
        if (consent != null) {
            consents.save(consent.forApplication(saved.getId()));
        }
        return autoShortlist ? systemShortlist(saved) : saved;
    }

    /** Moves an APPLIED application to SHORTLISTED with actor SYSTEM (auto-shortlist and auto-screen). */
    @Transactional
    public Application systemShortlist(Application application) {
        ApplicationStatus old = application.getStatus();
        application.changeStatus(ApplicationStatus.SHORTLISTED, null);
        application.markAutoShortlisted();
        Application saved = applications.saveAndFlush(application);
        history.record(saved, old, ApplicationStatus.SHORTLISTED, ActorType.SYSTEM, null, AutoShortlist.REASON);
        outbox.statusChanged(saved, old, null, ActorType.SYSTEM.name(), AutoShortlist.REASON);
        return saved;
    }

    @Transactional
    public Application changeStatus(Application application, ApplicationStatus newStatus, String note, UUID actorId) {
        ApplicationStatus old = application.getStatus();
        application.changeStatus(newStatus, note);
        Application saved = applications.saveAndFlush(application);
        history.record(saved, old, newStatus, ActorType.USER, actorId, note);
        outbox.statusChanged(saved, old, actorId);
        if (newStatus == ApplicationStatus.SELECTED) {
            placements.recordOnCampus(saved, actorId);
        }
        return saved;
    }

    /** Builds the consent once the application id exists (same transaction). */
    @FunctionalInterface
    public interface ConsentFactory {
        Consent forApplication(UUID applicationId);
    }
}
