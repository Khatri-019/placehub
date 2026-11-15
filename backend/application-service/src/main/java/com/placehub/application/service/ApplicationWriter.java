package com.placehub.application.service;

import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.entity.Application;
import com.placehub.application.messaging.OutboxWriter;
import com.placehub.application.repository.ApplicationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** The only place that mutates applications: each change and its outbox row commit in one transaction. */
@Component
public class ApplicationWriter {

    private final ApplicationRepository applications;
    private final OutboxWriter outbox;

    public ApplicationWriter(ApplicationRepository applications, OutboxWriter outbox) {
        this.applications = applications;
        this.outbox = outbox;
    }

    /** Throws DataIntegrityViolationException on the (job_id, student_user_id) unique constraint. */
    @Transactional
    public Application create(Application application) {
        Application saved = applications.saveAndFlush(application);
        outbox.applicationCreated(saved);
        return saved;
    }

    @Transactional
    public Application changeStatus(Application application, ApplicationStatus newStatus, String note, UUID actorId) {
        ApplicationStatus old = application.getStatus();
        application.changeStatus(newStatus, note);
        Application saved = applications.saveAndFlush(application);
        outbox.statusChanged(saved, old, actorId);
        return saved;
    }
}
