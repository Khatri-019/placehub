package com.placehub.notification.messaging;

import com.placehub.common.web.CorrelationIdFilter;
import com.placehub.notification.service.NotificationFactory;
import com.placehub.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/** Consumes application events (group notification-service) and stores in-app notifications idempotently. */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final EventParser parser;
    private final NotificationService notifications;
    private final Clock clock;

    public NotificationConsumer(EventParser parser, NotificationService notifications, Clock clock) {
        this.parser = parser;
        this.notifications = notifications;
        this.clock = clock;
    }

    @KafkaListener(topics = ApplicationEvents.CREATED_TOPIC, groupId = "notification-service")
    public void onCreated(ConsumerRecord<String, String> record) {
        withCorrelation(record, () -> {
            var event = parser.parseCreated(record.value());
            int stored = notifications.store(NotificationFactory.forCreated(event, clock.instant()));
            log.info("Processed {} event {} for application {} ({} new notifications)", event.eventType(),
                    event.eventId(), event.applicationId(), stored);
        });
    }

    @KafkaListener(topics = ApplicationEvents.STATUS_CHANGED_TOPIC, groupId = "notification-service")
    public void onStatusChanged(ConsumerRecord<String, String> record) {
        withCorrelation(record, () -> {
            var event = parser.parseStatusChanged(record.value());
            int stored = notifications.store(NotificationFactory.forStatusChanged(event, clock.instant()));
            log.info("Processed {} event {} ({} -> {}) ({} new notifications)", event.eventType(), event.eventId(),
                    event.oldStatus(), event.newStatus(), stored);
        });
    }

    private static void withCorrelation(ConsumerRecord<String, String> record, Runnable work) {
        Header header = record.headers().lastHeader(CorrelationIdFilter.HEADER);
        if (header != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY,
                    CorrelationIdFilter.sanitize(new String(header.value(), StandardCharsets.UTF_8)));
        }
        try {
            work.run();
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
