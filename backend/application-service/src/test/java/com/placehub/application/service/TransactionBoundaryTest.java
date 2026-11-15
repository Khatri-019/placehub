package com.placehub.application.service;

import com.placehub.application.domain.ApplicationStatus;
import com.placehub.application.entity.Application;
import com.placehub.application.messaging.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox row must be written in the same transaction as the application change: writer methods open a
 * transaction and the outbox writer refuses to run outside one (MANDATORY).
 */
class TransactionBoundaryTest {

    @Test
    void writerMethodsAreTransactional() throws Exception {
        assertThat(ApplicationWriter.class.getMethod("create", Application.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(ApplicationWriter.class.getMethod("changeStatus", Application.class, ApplicationStatus.class,
                String.class, UUID.class).getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void outboxWritesRequireAnExistingTransaction() throws Exception {
        assertThat(OutboxWriter.class.getMethod("applicationCreated", Application.class)
                .getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(OutboxWriter.class.getMethod("statusChanged", Application.class, ApplicationStatus.class,
                UUID.class).getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
